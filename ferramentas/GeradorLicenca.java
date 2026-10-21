import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Gerador de chaves de licença do Caravellus. USO EXCLUSIVO DO FORNECEDOR: não vai no instalador.
 *
 * Executar com o Java 25 (sem compilar), ou pelo gerar-licenca.bat na raiz do projeto:
 *   java ferramentas/GeradorLicenca.java                          abre a janela do gerador
 *   java ferramentas/GeradorLicenca.java chaves                   cria o par de chaves (uma única vez) e mostra
 *                                                                 a chave pública para LicencaService.CHAVE_PUBLICA
 *   java ferramentas/GeradorLicenca.java gerar "<cliente>" <cod> [e-mail] [pasta]
 *                                                                 gera a chave para o código da máquina mostrado
 *                                                                 na tela de ativação do cliente. Com o e-mail, ele vai
 *                                                                 na licença e o arquivo é enviado ao cliente; com a
 *                                                                 pasta (ou arquivo), salva também o arquivo de licença
 *
 * Arquivo de licença (privatekey.lic): o cliente importa na tela de ativação em vez de colar a chave.
 * Apesar do nome, NÃO contém a chave privada: só a chave de licença assinada, entre as linhas
 * INICIO_ARQUIVO e FIM_ARQUIVO, após um cabeçalho informativo (cliente, máquina, data).
 *
 * A chave privada fica em %USERPROFILE%\.portocabral\licenca-privada.key (ou no caminho da
 * variável PORTOCABRAL_CHAVE_PRIVADA). Quem tiver esse arquivo consegue gerar licenças:
 * guarde uma cópia de segurança e nunca a coloque no Git nem no instalador.
 * Se ela for perdida, licenças novas exigem novo par de chaves e nova versão do sistema.
 * As licenças emitidas ficam registradas em licencas-emitidas.csv, na mesma pasta da chave privada.
 *
 * Envio da licença por e-mail: usa a mesma conta SMTP do sistema (Brevo). Lê o primeiro que existir:
 * %USERPROFILE%\.portocabral\email.properties ou backend/src/main/resources/email-padrao.properties
 * (a partir da pasta atual; o gerar-licenca.bat roda na raiz do projeto), com as chaves
 * spring.mail.username, spring.mail.password, app.mail.remetente e, opcionais, spring.mail.host e spring.mail.port.
 * O pedido de licença do cliente chega em app.licenca.email-fornecedor (backend), com cliente, e-mail e código.
 *
 * O formato da chave precisa ser o mesmo que LicencaService (backend) espera:
 *   PC1.<payload em Base64 URL>.<assinatura Ed25519 do payload em Base64 URL>
 *   payload (UTF-8): linhas "cliente=...", "email=..." (opcional), "maquina=XXXX-XXXX-XXXX-XXXX", "emitida=AAAA-MM-DD"
 * Mensagens no console sem acento: o console do Windows nem sempre usa UTF-8.
 */
public class GeradorLicenca {

    private static final String PREFIXO = "PC1";
    private static final Pattern CODIGO_MAQUINA = Pattern.compile("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    /** E-mail ficou na última coluna: linhas antigas, sem ela, continuam válidas */
    private static final String CABECALHO_HISTORICO = "emitida;cliente;maquina;chave;email";

    /** Arquivo de licença enviado ao cliente; delimitadores iguais aos de LicencaService (backend) */
    static final String NOME_ARQUIVO_LICENCA = "privatekey.lic";
    private static final String INICIO_ARQUIVO = "-----BEGIN PORTO CABRAL LICENCA-----";
    private static final String FIM_ARQUIVO = "-----END PORTO CABRAL LICENCA-----";

    /** Uma licença emitida (linha do histórico) */
    record Emissao(LocalDateTime emitida, String cliente, String maquina, String chave, String email) {}

    /** Conta SMTP usada para enviar a licença ao cliente */
    record ContaEmail(String host, int porta, String usuario, String senha, String remetente, Path origem) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            abrirJanela();
            return;
        }
        try {
            if (args.length == 1 && args[0].equals("chaves")) {
                String publica = criarChaves();
                System.out.println("Chave privada criada em: " + arquivoChavePrivada());
                System.out.println("FACA UMA COPIA DE SEGURANCA desse arquivo e nao o coloque no Git.");
                System.out.println();
                System.out.println("Chave publica (colar em LicencaService.CHAVE_PUBLICA):");
                System.out.println(publica);
            } else if (args.length >= 3 && args.length <= 5 && args[0].equals("gerar")) {
                // Argumentos opcionais em qualquer ordem: o que tem @ é o e-mail, o outro é a pasta
                String email = "";
                String pasta = null;
                for (int i = 3; i < args.length; i++) {
                    if (args[i].contains("@")) email = args[i];
                    else pasta = args[i];
                }
                Emissao e = gerar(args[1], args[2], email);
                System.out.println("Cliente: " + e.cliente());
                if (!e.email().isEmpty()) System.out.println("E-mail:  " + e.email());
                System.out.println("Maquina: " + e.maquina());
                System.out.println();
                System.out.println("Chave de licenca (enviar ao cliente):");
                System.out.println(e.chave());
                if (pasta != null) {
                    Path destino = salvarArquivo(e, Path.of(pasta));
                    System.out.println();
                    System.out.println("Arquivo de licenca salvo em: " + destino);
                }
                if (!e.email().isEmpty()) {
                    enviarLicenca(e);
                    System.out.println();
                    System.out.println("Arquivo de licenca enviado para " + e.email() + ".");
                }
            } else {
                System.out.println("Uso:");
                System.out.println("  java ferramentas/GeradorLicenca.java                 (abre a janela)");
                System.out.println("  java ferramentas/GeradorLicenca.java chaves");
                System.out.println("  java ferramentas/GeradorLicenca.java gerar \"Nome do cliente\" XXXX-XXXX-XXXX-XXXX [e-mail] [pasta ou arquivo .lic]");
                System.exit(1);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERRO: " + e.getMessage());
            System.exit(1);
        }
    }

    // ── Regras (usadas pela linha de comando e pela janela) ──────────────────────────────────

    static Path arquivoChavePrivada() {
        String caminho = System.getenv("PORTOCABRAL_CHAVE_PRIVADA");
        if (caminho != null && !caminho.isBlank()) return Path.of(caminho).toAbsolutePath();
        return Path.of(System.getProperty("user.home"), ".portocabral", "licenca-privada.key");
    }

    static Path arquivoHistorico() {
        return arquivoChavePrivada().resolveSibling("licencas-emitidas.csv");
    }

    /** Cria o par de chaves e devolve a chave pública (Base64). Nunca sobrescreve uma chave existente. */
    static String criarChaves() throws Exception {
        Path arquivo = arquivoChavePrivada();
        if (Files.exists(arquivo)) {
            // Sobrescrever invalidaria todas as licenças já emitidas
            throw new IllegalStateException("Ja existe uma chave privada em " + arquivo + ". Ela NAO foi substituida.");
        }
        KeyPair par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, Base64.getEncoder().encodeToString(par.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        return Base64.getEncoder().encodeToString(par.getPublic().getEncoded());
    }

    static String normalizarMaquina(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase();
    }

    /** Gera a chave de licença, registra no histórico e devolve a emissão. E-mail vazio: licença sem e-mail. */
    static Emissao gerar(String cliente, String codigoMaquina, String email) throws Exception {
        cliente = cliente == null ? "" : cliente.trim();
        email = email == null ? "" : email.trim();
        String maquina = normalizarMaquina(codigoMaquina);
        if (cliente.isEmpty() || cliente.contains("\n") || cliente.contains("\r")) {
            throw new IllegalArgumentException("Nome do cliente invalido.");
        }
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("E-mail do cliente invalido: " + email);
        }
        if (!CODIGO_MAQUINA.matcher(maquina).matches()) {
            throw new IllegalArgumentException("Codigo da maquina invalido: " + codigoMaquina + " (esperado XXXX-XXXX-XXXX-XXXX)");
        }

        Path arquivo = arquivoChavePrivada();
        if (!Files.exists(arquivo)) {
            throw new IllegalStateException("Chave privada nao encontrada em " + arquivo + ". Crie o par de chaves antes.");
        }
        byte[] pkcs8 = Base64.getDecoder().decode(Files.readString(arquivo, StandardCharsets.US_ASCII).trim());
        PrivateKey privada = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));

        String payload = "cliente=" + cliente + (email.isEmpty() ? "" : "\nemail=" + email)
                + "\nmaquina=" + maquina + "\nemitida=" + LocalDate.now();
        byte[] bytesPayload = payload.getBytes(StandardCharsets.UTF_8);
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initSign(privada);
        assinatura.update(bytesPayload);

        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String chave = PREFIXO + "." + b64.encodeToString(bytesPayload) + "." + b64.encodeToString(assinatura.sign());

        Emissao emissao = new Emissao(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), cliente, maquina, chave, email);
        registrar(emissao);
        return emissao;
    }

    /** Conteúdo do arquivo de licença: cabeçalho informativo + chave em linhas de 64 caracteres */
    static String conteudoArquivo(Emissao e) {
        StringBuilder texto = new StringBuilder();
        texto.append(INICIO_ARQUIVO).append("\r\n");
        texto.append("Cliente: ").append(e.cliente()).append("\r\n");
        if (!e.email().isEmpty()) texto.append("E-mail: ").append(e.email()).append("\r\n");
        texto.append("Maquina: ").append(e.maquina()).append("\r\n");
        texto.append("Emitida: ").append(e.emitida().toLocalDate()).append("\r\n\r\n");
        String chave = e.chave();
        for (int i = 0; i < chave.length(); i += 64) {
            texto.append(chave, i, Math.min(i + 64, chave.length())).append("\r\n");
        }
        texto.append(FIM_ARQUIVO).append("\r\n");
        return texto.toString();
    }

    /** Salva o arquivo de licença; se o destino for uma pasta, usa o nome padrão (privatekey.lic) */
    static Path salvarArquivo(Emissao e, Path destino) throws IOException {
        if (Files.isDirectory(destino)) destino = destino.resolve(NOME_ARQUIVO_LICENCA);
        Path pasta = destino.toAbsolutePath().getParent();
        if (pasta != null) Files.createDirectories(pasta);
        Files.writeString(destino, conteudoArquivo(e), StandardCharsets.UTF_8);
        return destino.toAbsolutePath();
    }

    // ── Envio da licença por e-mail (SMTP com STARTTLS, sem bibliotecas externas) ────────────

    /** Primeiro arquivo de conta que existir: o da pasta da chave privada ou o email-padrao.properties do backend */
    static ContaEmail contaEmail() throws IOException {
        List<Path> candidatos = List.of(
                arquivoChavePrivada().resolveSibling("email.properties"),
                Path.of("backend", "src", "main", "resources", "email-padrao.properties").toAbsolutePath());
        for (Path arquivo : candidatos) {
            if (!Files.exists(arquivo)) continue;
            Properties p = new Properties();
            try (InputStream entrada = Files.newInputStream(arquivo)) {
                p.load(entrada);
            }
            String usuario = p.getProperty("spring.mail.username", "").trim();
            String senha = p.getProperty("spring.mail.password", "").trim();
            String remetente = p.getProperty("app.mail.remetente", usuario).trim();
            if (usuario.isEmpty() || senha.isEmpty() || remetente.isEmpty()) {
                throw new IllegalStateException("Conta de e-mail incompleta em " + arquivo
                        + " (preencha spring.mail.username, spring.mail.password e app.mail.remetente).");
            }
            return new ContaEmail(p.getProperty("spring.mail.host", "smtp-relay.brevo.com").trim(),
                    Integer.parseInt(p.getProperty("spring.mail.port", "587").trim()), usuario, senha, remetente, arquivo);
        }
        throw new IllegalStateException("Conta de e-mail nao configurada. Crie " + candidatos.get(0)
                + " com spring.mail.username, spring.mail.password e app.mail.remetente.");
    }

    /** Envia o arquivo de licença (anexo) para o e-mail do cliente gravado na licença */
    static void enviarLicenca(Emissao e) throws IOException {
        if (e.email().isEmpty()) {
            throw new IllegalArgumentException("Esta licenca nao tem o e-mail do cliente.");
        }
        String texto = """
                Olá, %s!

                Segue em anexo o arquivo de licença do Caravellus para o computador que fez o pedido.

                Para ativar o sistema:
                1. Salve o arquivo anexo (%s) no computador.
                2. Abra o Caravellus e, na tela de ativação, clique em "Importar arquivo de licença".
                3. Escolha o arquivo salvo.

                A licença vale somente para esse computador.
                """.formatted(e.cliente(), NOME_ARQUIVO_LICENCA);
        enviarEmail(contaEmail(), e.email(), "Caravellus - Arquivo de licença", texto,
                NOME_ARQUIVO_LICENCA, conteudoArquivo(e).getBytes(StandardCharsets.UTF_8));
    }

    static void enviarEmail(ContaEmail conta, String para, String assunto, String texto, String nomeAnexo, byte[] anexo)
            throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(conta.host(), conta.porta()), 20_000);
            socket.setSoTimeout(30_000);
            boolean tlsDireto = conta.porta() == 465;
            if (tlsDireto) socket = tls(socket, conta.host());
            Smtp smtp = new Smtp(socket);
            smtp.esperar(220);
            smtp.comando("EHLO caravellus", 250);
            if (!tlsDireto) {
                smtp.comando("STARTTLS", 220);
                socket = tls(socket, conta.host());
                smtp = new Smtp(socket);
                smtp.comando("EHLO caravellus", 250);
            }
            Base64.Encoder b64 = Base64.getEncoder();
            smtp.comando("AUTH LOGIN", 334);
            smtp.comando(b64.encodeToString(conta.usuario().getBytes(StandardCharsets.UTF_8)), 334);
            smtp.comando(b64.encodeToString(conta.senha().getBytes(StandardCharsets.UTF_8)), 235);
            smtp.comando("MAIL FROM:<" + conta.remetente() + ">", 250);
            smtp.comando("RCPT TO:<" + para + ">", 250);
            smtp.comando("DATA", 354);
            smtp.comando(mensagemMime(conta.remetente(), para, assunto, texto, nomeAnexo, anexo) + ".", 250);
            smtp.comando("QUIT", 221);
        } finally {
            socket.close();
        }
    }

    /** Liga o TLS sobre a conexão e confere o certificado do servidor (nome do host) */
    private static Socket tls(Socket socket, String host) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(socket, host, socket.getPort(), true);
        SSLParameters parametros = ssl.getSSLParameters();
        parametros.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(parametros);
        ssl.startHandshake();
        return ssl;
    }

    /** Conversa SMTP: cada comando espera o código de resposta indicado */
    private static final class Smtp {
        private final BufferedReader entrada;
        private final OutputStream saida;

        Smtp(Socket socket) throws IOException {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            saida = socket.getOutputStream();
        }

        void comando(String linha, int esperado) throws IOException {
            saida.write((linha + "\r\n").getBytes(StandardCharsets.US_ASCII));
            saida.flush();
            esperar(esperado);
        }

        /** Respostas de várias linhas: "250-..." continua, "250 ..." termina */
        void esperar(int esperado) throws IOException {
            String linha;
            do {
                linha = entrada.readLine();
                if (linha == null) throw new IOException("O servidor de e-mail encerrou a conexao.");
            } while (linha.length() > 3 && linha.charAt(3) == '-');
            if (!linha.startsWith(String.valueOf(esperado))) {
                throw new IOException("O servidor de e-mail recusou o envio: " + linha);
            }
        }
    }

    /** Mensagem com texto e anexo; tudo em Base64, então só há caracteres ASCII e nenhuma linha começa com "." */
    static String mensagemMime(String remetente, String para, String assunto, String texto, String nomeAnexo, byte[] anexo) {
        String limite = "----=_Caravellus_" + UUID.randomUUID();
        Base64.Encoder linhas = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII));
        String crlf = texto.replace("\r\n", "\n").replace("\n", "\r\n");
        return "From: " + codificado("Caravellus") + " <" + remetente + ">\r\n"
                + "To: <" + para + ">\r\n"
                + "Subject: " + codificado(assunto) + "\r\n"
                + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n"
                + "Message-ID: <" + UUID.randomUUID() + "@caravellus>\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"" + limite + "\"\r\n"
                + "\r\n"
                + "--" + limite + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + linhas.encodeToString(crlf.getBytes(StandardCharsets.UTF_8)) + "\r\n"
                + "--" + limite + "\r\n"
                + "Content-Type: application/octet-stream; name=\"" + nomeAnexo + "\"\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "Content-Disposition: attachment; filename=\"" + nomeAnexo + "\"\r\n"
                + "\r\n"
                + linhas.encodeToString(anexo) + "\r\n"
                + "--" + limite + "--\r\n";
    }

    /** Cabeçalho com acentos (RFC 2047) */
    private static String codificado(String texto) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /**
     * Preenche cliente, e-mail e código a partir do texto do e-mail de pedido de licença
     * (linhas "Cliente: ...", "E-mail: ..." e "Código da máquina: ..."). Campo ausente: null.
     */
    static String[] lerPedido(String texto) {
        String[] campos = new String[3];
        for (String linha : texto.split("\\R")) {
            String l = linha.trim();
            if (l.startsWith("Cliente:")) campos[0] = l.substring(8).trim();
            else if (l.startsWith("E-mail:")) campos[1] = l.substring(7).trim();
            else if (l.startsWith("Código da máquina:")) campos[2] = l.substring(18).trim();
        }
        return campos;
    }

    /** Histórico em CSV (separador ;), uma licença por linha */
    private static void registrar(Emissao e) throws IOException {
        Path arquivo = arquivoHistorico();
        String linha = String.join(";", campoCsv(e.emitida().toString()), campoCsv(e.cliente()),
                campoCsv(e.maquina()), campoCsv(e.chave()), campoCsv(e.email())) + "\r\n";
        if (!Files.exists(arquivo)) {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, CABECALHO_HISTORICO + "\r\n", StandardCharsets.UTF_8);
        }
        Files.writeString(arquivo, linha, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** Licenças emitidas, da mais recente para a mais antiga */
    static List<Emissao> lerHistorico() throws IOException {
        List<Emissao> lista = new ArrayList<>();
        Path arquivo = arquivoHistorico();
        if (!Files.exists(arquivo)) return lista;
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            if (linha.isBlank() || linha.equals(CABECALHO_HISTORICO)) continue;
            List<String> c = lerLinhaCsv(linha);
            if (c.size() < 4) continue;
            try {
                lista.add(new Emissao(LocalDateTime.parse(c.get(0)), c.get(1), c.get(2), c.get(3), c.size() > 4 ? c.get(4) : ""));
            } catch (RuntimeException ignorada) {
                // Linha corrompida ou editada à mão: mantém as demais
            }
        }
        lista.sort((a, b) -> b.emitida().compareTo(a.emitida()));
        return lista;
    }

    private static String campoCsv(String valor) {
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    private static List<String> lerLinhaCsv(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (entreAspas) {
                if (ch == '"' && i + 1 < linha.length() && linha.charAt(i + 1) == '"') { atual.append('"'); i++; }
                else if (ch == '"') entreAspas = false;
                else atual.append(ch);
            } else if (ch == '"') {
                entreAspas = true;
            } else if (ch == ';') {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    // ── Janela ───────────────────────────────────────────────────────────────────────────────

    static void abrirJanela() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) {
                // Sem o visual do Windows, usa o padrão do Java
            }
            new Janela().setVisible(true);
        });
    }

    static class Janela extends JFrame {

        private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        private final JLabel situacaoChave = new JLabel();
        private final JButton criarChaves = new JButton("Criar par de chaves");
        private final JTextField cliente = new JTextField(28);
        private final JTextField email = new JTextField(28);
        private final JButton colarPedido = new JButton("Colar pedido");
        private final JTextField maquina = new JTextField(20);
        private final JButton gerar = new JButton("Gerar chave");
        private final JTextArea chave = new JTextArea(4, 60);
        private final JButton copiar = new JButton("Copiar chave");
        private final JButton salvar = new JButton("Salvar arquivo de licença...");
        private final JButton enviar = new JButton("Enviar por e-mail");
        /** Licença exibida na tela (recém-gerada ou selecionada no histórico) */
        private Emissao atual;
        private Path ultimaPasta = Path.of(System.getProperty("user.home"), "Desktop");
        private final JLabel mensagem = new JLabel(" ");
        private final JTextField busca = new JTextField(20);
        private final DefaultTableModel modelo = new DefaultTableModel(new String[] {"Emitida em", "Cliente", "E-mail", "Máquina"}, 0) {
            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
        private final JTable tabela = new JTable(modelo);
        private final TableRowSorter<DefaultTableModel> ordenador = new TableRowSorter<>(modelo);
        private List<Emissao> historico = new ArrayList<>();

        Janela() {
            super("Gerador de Licenças - Caravellus");
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JPanel conteudo = new JPanel(new BorderLayout(0, 12));
            conteudo.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            conteudo.add(painelChavePrivada(), BorderLayout.NORTH);
            conteudo.add(painelGeracao(), BorderLayout.CENTER);
            conteudo.add(painelHistorico(), BorderLayout.SOUTH);
            setContentPane(conteudo);

            getRootPane().setDefaultButton(gerar);
            atualizarSituacaoChave();
            carregarHistorico();
            pack();
            setMinimumSize(getSize());
            setLocationRelativeTo(null);
        }

        private JPanel painelChavePrivada() {
            JPanel painel = new JPanel(new BorderLayout(8, 0));
            painel.add(situacaoChave, BorderLayout.CENTER);
            painel.add(criarChaves, BorderLayout.EAST);
            criarChaves.addActionListener(e -> criarParDeChaves());
            return painel;
        }

        private JPanel painelGeracao() {
            JPanel painel = new JPanel(new GridBagLayout());
            painel.setBorder(BorderFactory.createTitledBorder("Nova licença"));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 6, 4, 6);
            g.anchor = GridBagConstraints.WEST;

            g.gridx = 0; g.gridy = 0;
            painel.add(new JLabel("Cliente:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            cliente.setMinimumSize(cliente.getPreferredSize());
            painel.add(cliente, g);

            g.gridx = 0; g.gridy = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0;
            painel.add(new JLabel("E-mail do cliente:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            email.setToolTipText("Vai na licença e recebe o arquivo de licença (Enviar por e-mail)");
            email.setMinimumSize(email.getPreferredSize());
            painel.add(email, g);

            g.gridx = 0; g.gridy = 2; g.fill = GridBagConstraints.NONE; g.weightx = 0;
            painel.add(new JLabel("Código da máquina:"), g);
            g.gridx = 1;
            maquina.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            maquina.setToolTipText("Código mostrado na tela de ativação do cliente (XXXX-XXXX-XXXX-XXXX)");
            maquina.setMinimumSize(maquina.getPreferredSize());
            painel.add(maquina, g);

            g.gridx = 1; g.gridy = 3;
            JPanel botoesGerar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            gerar.addActionListener(e -> gerarChave());
            botoesGerar.add(gerar);
            botoesGerar.add(Box.createHorizontalStrut(8));
            colarPedido.setToolTipText("Preenche os campos com o texto copiado do e-mail de pedido de licença");
            colarPedido.addActionListener(e -> colarPedidoDaAreaDeTransferencia());
            botoesGerar.add(colarPedido);
            painel.add(botoesGerar, g);

            g.gridx = 0; g.gridy = 4; g.anchor = GridBagConstraints.NORTHWEST;
            painel.add(new JLabel("Chave de licença:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.BOTH; g.weighty = 1;
            chave.setEditable(false);
            chave.setLineWrap(true);
            chave.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            painel.add(new JScrollPane(chave), g);

            g.gridx = 1; g.gridy = 5; g.fill = GridBagConstraints.NONE; g.weighty = 0; g.anchor = GridBagConstraints.WEST;
            JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            copiar.setEnabled(false);
            copiar.addActionListener(e -> copiarChave());
            acoes.add(copiar);
            acoes.add(Box.createHorizontalStrut(8));
            salvar.setEnabled(false);
            salvar.addActionListener(e -> salvarArquivoLicenca());
            acoes.add(salvar);
            acoes.add(Box.createHorizontalStrut(8));
            enviar.setEnabled(false);
            enviar.addActionListener(e -> enviarPorEmail());
            acoes.add(enviar);
            acoes.add(Box.createHorizontalStrut(12));
            acoes.add(mensagem);
            painel.add(acoes, g);
            return painel;
        }

        private JPanel painelHistorico() {
            JPanel painel = new JPanel(new BorderLayout(0, 6));
            painel.setBorder(BorderFactory.createTitledBorder("Licenças emitidas"));

            JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            filtro.add(new JLabel("Buscar:"));
            filtro.add(busca);
            filtro.add(new JLabel("(clique em uma linha para ver a chave)"));
            painel.add(filtro, BorderLayout.NORTH);

            tabela.setRowSorter(ordenador);
            tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tabela.getColumnModel().getColumn(0).setPreferredWidth(120);
            tabela.getColumnModel().getColumn(1).setPreferredWidth(220);
            tabela.getColumnModel().getColumn(2).setPreferredWidth(220);
            tabela.getColumnModel().getColumn(3).setPreferredWidth(170);
            tabela.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) mostrarSelecionada();
            });
            JScrollPane rolagem = new JScrollPane(tabela);
            rolagem.setPreferredSize(new Dimension(740, 200));
            painel.add(rolagem, BorderLayout.CENTER);

            busca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
            });
            return painel;
        }

        private void atualizarSituacaoChave() {
            Path arquivo = arquivoChavePrivada();
            boolean existe = Files.exists(arquivo);
            situacaoChave.setText(existe
                    ? "Chave privada: " + arquivo
                    : "Chave privada não encontrada. Crie o par de chaves para começar.");
            situacaoChave.setForeground(existe ? UIManager.getColor("Label.foreground") : new Color(0xB4, 0x53, 0x09));
            situacaoChave.setToolTipText(arquivo.toString());
            // Caminho longo é cortado com "..." em vez de alargar a janela (caminho completo na dica)
            situacaoChave.setPreferredSize(new Dimension(1, situacaoChave.getPreferredSize().height));
            criarChaves.setVisible(!existe);
            gerar.setEnabled(existe);
        }

        private void criarParDeChaves() {
            int resposta = JOptionPane.showConfirmDialog(this,
                    "Criar um novo par de chaves?\n\nA chave pública gerada precisa ser colada em LicencaService.CHAVE_PUBLICA\n"
                            + "e o sistema recompilado; licenças de outro par deixam de valer.",
                    "Criar par de chaves", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (resposta != JOptionPane.OK_OPTION) return;
            try {
                String publica = criarChaves();
                JTextArea texto = new JTextArea(publica, 2, 50);
                texto.setEditable(false);
                texto.setLineWrap(true);
                JOptionPane.showMessageDialog(this, new Object[] {
                        "Chave privada criada em " + arquivoChavePrivada() + ".",
                        "FAÇA UMA CÓPIA DE SEGURANÇA desse arquivo e não o coloque no Git.",
                        " ",
                        "Chave pública (colar em LicencaService.CHAVE_PUBLICA):",
                        texto}, "Par de chaves criado", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                erro(ex);
            }
            atualizarSituacaoChave();
        }

        private void gerarChave() {
            if (cliente.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Informe o nome do cliente.", "Gerar chave", JOptionPane.WARNING_MESSAGE);
                cliente.requestFocus();
                return;
            }
            String codigo = normalizarMaquina(maquina.getText());
            maquina.setText(codigo);
            if (!CODIGO_MAQUINA.matcher(codigo).matches()) {
                JOptionPane.showMessageDialog(this, "Código da máquina inválido. Formato esperado: XXXX-XXXX-XXXX-XXXX.",
                        "Gerar chave", JOptionPane.WARNING_MESSAGE);
                maquina.requestFocus();
                return;
            }
            Emissao anterior = historico.stream().filter(h -> h.maquina().equals(codigo)).findFirst().orElse(null);
            if (anterior != null) {
                int resposta = JOptionPane.showConfirmDialog(this,
                        "Esta máquina já recebeu uma licença em " + DATA.format(anterior.emitida())
                                + " (cliente: " + anterior.cliente() + ").\nGerar outra mesmo assim?",
                        "Máquina já licenciada", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (resposta != JOptionPane.YES_OPTION) return;
            }
            try {
                Emissao emissao = gerar(cliente.getText(), codigo, email.getText());
                carregarHistorico();
                exibir(emissao);
                copiarChave();
                mensagem.setText("Chave gerada e copiada para " + emissao.cliente() + ".");
                if (!emissao.email().isEmpty() && JOptionPane.showConfirmDialog(this,
                        "Enviar o arquivo de licença para " + emissao.email() + " agora?", "Enviar por e-mail",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    enviarPorEmail();
                }
            } catch (Exception ex) {
                erro(ex);
            }
        }

        /** Envio em segundo plano: a conexão com o servidor de e-mail pode levar alguns segundos */
        private void enviarPorEmail() {
            Emissao emissao = atual;
            if (emissao == null || emissao.email().isEmpty()) return;
            enviar.setEnabled(false);
            mensagem.setText("Enviando para " + emissao.email() + "...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    enviarLicenca(emissao);
                    return null;
                }

                @Override
                protected void done() {
                    enviar.setEnabled(atual != null && !atual.email().isEmpty());
                    try {
                        get();
                        mensagem.setText("Arquivo de licença enviado para " + emissao.email() + ".");
                    } catch (java.util.concurrent.ExecutionException ex) {
                        mensagem.setText("Falha no envio.");
                        erro(ex.getCause() instanceof Exception causa ? causa : ex);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }.execute();
        }

        private void colarPedidoDaAreaDeTransferencia() {
            String texto;
            try {
                texto = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            } catch (Exception ex) {
                texto = "";
            }
            String[] campos = lerPedido(texto);
            if (campos[0] == null && campos[1] == null && campos[2] == null) {
                JOptionPane.showMessageDialog(this, "Copie o texto do e-mail de pedido de licença e tente de novo.",
                        "Colar pedido", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (campos[0] != null) cliente.setText(campos[0]);
            if (campos[1] != null) email.setText(campos[1]);
            if (campos[2] != null) maquina.setText(normalizarMaquina(campos[2]));
            mensagem.setText("Dados do pedido preenchidos. Confira e clique em Gerar chave.");
        }

        private void copiarChave() {
            if (chave.getText().isEmpty()) return;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(chave.getText()), null);
            mensagem.setText("Chave copiada.");
        }

        private void salvarArquivoLicenca() {
            if (atual == null) return;
            JFileChooser seletor = new JFileChooser(Files.isDirectory(ultimaPasta) ? ultimaPasta.toFile() : null);
            seletor.setDialogTitle("Salvar arquivo de licença - " + atual.cliente());
            seletor.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Licença Caravellus (*.lic)", "lic"));
            seletor.setSelectedFile(new java.io.File(seletor.getCurrentDirectory(), NOME_ARQUIVO_LICENCA));
            if (seletor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

            Path destino = seletor.getSelectedFile().toPath();
            if (!destino.getFileName().toString().toLowerCase().endsWith(".lic")) {
                destino = destino.resolveSibling(destino.getFileName() + ".lic");
            }
            if (Files.exists(destino) && JOptionPane.showConfirmDialog(this,
                    destino.getFileName() + " já existe nessa pasta. Substituir?", "Salvar arquivo de licença",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                Path salvo = salvarArquivo(atual, destino);
                ultimaPasta = salvo.getParent();
                mensagem.setText("Arquivo salvo: " + salvo.getFileName() + " (" + atual.cliente() + ").");
            } catch (IOException ex) {
                erro(ex);
            }
        }

        private void exibir(Emissao emissao) {
            atual = emissao;
            chave.setText(emissao.chave());
            chave.setCaretPosition(0);
            copiar.setEnabled(true);
            salvar.setEnabled(true);
            enviar.setEnabled(!emissao.email().isEmpty());
            enviar.setToolTipText(emissao.email().isEmpty() ? "Licença sem e-mail do cliente" : "Enviar para " + emissao.email());
        }

        private void mostrarSelecionada() {
            int linha = tabela.getSelectedRow();
            if (linha < 0) return;
            Emissao e = historico.get(tabela.convertRowIndexToModel(linha));
            exibir(e);
            mensagem.setText("Chave emitida para " + e.cliente() + " em " + DATA.format(e.emitida()) + ".");
        }

        private void carregarHistorico() {
            try {
                historico = lerHistorico();
            } catch (IOException ex) {
                erro(ex);
                historico = new ArrayList<>();
            }
            modelo.setRowCount(0);
            for (Emissao e : historico) {
                modelo.addRow(new Object[] {DATA.format(e.emitida()), e.cliente(), e.email(), e.maquina()});
            }
        }

        private void filtrar() {
            String texto = busca.getText().trim();
            ordenador.setRowFilter(texto.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2, 3));
        }

        private void erro(Exception ex) {
            String texto = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            JOptionPane.showMessageDialog(this, texto, "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
