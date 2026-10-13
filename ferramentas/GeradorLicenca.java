import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Gerador de chaves de licença do Caravellus. USO EXCLUSIVO DO FORNECEDOR: não vai no instalador.
 *
 * Executar com o Java 25 (sem compilar), ou pelo gerar-licenca.bat na raiz do projeto:
 *   java ferramentas/GeradorLicenca.java                          abre a janela do gerador
 *   java ferramentas/GeradorLicenca.java chaves                   cria o par de chaves (uma única vez) e mostra
 *                                                                 a chave pública para LicencaService.CHAVE_PUBLICA
 *   java ferramentas/GeradorLicenca.java gerar "<cliente>" <cod>  gera a chave para o código da máquina mostrado
 *                                                                 na tela de ativação do cliente; com um 4º argumento
 *                                                                 (pasta ou arquivo) salva também o arquivo de licença
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
 * O formato da chave precisa ser o mesmo que LicencaService (backend) espera:
 *   PC1.<payload em Base64 URL>.<assinatura Ed25519 do payload em Base64 URL>
 *   payload (UTF-8): linhas "cliente=...", "maquina=XXXX-XXXX-XXXX-XXXX", "emitida=AAAA-MM-DD"
 * Mensagens no console sem acento: o console do Windows nem sempre usa UTF-8.
 */
public class GeradorLicenca {

    private static final String PREFIXO = "PC1";
    private static final Pattern CODIGO_MAQUINA = Pattern.compile("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}");
    private static final String CABECALHO_HISTORICO = "emitida;cliente;maquina;chave";

    /** Arquivo de licença enviado ao cliente; delimitadores iguais aos de LicencaService (backend) */
    static final String NOME_ARQUIVO_LICENCA = "privatekey.lic";
    private static final String INICIO_ARQUIVO = "-----BEGIN PORTO CABRAL LICENCA-----";
    private static final String FIM_ARQUIVO = "-----END PORTO CABRAL LICENCA-----";

    /** Uma licença emitida (linha do histórico) */
    record Emissao(LocalDateTime emitida, String cliente, String maquina, String chave) {}

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
            } else if ((args.length == 3 || args.length == 4) && args[0].equals("gerar")) {
                Emissao e = gerar(args[1], args[2]);
                System.out.println("Cliente: " + e.cliente());
                System.out.println("Maquina: " + e.maquina());
                System.out.println();
                System.out.println("Chave de licenca (enviar ao cliente):");
                System.out.println(e.chave());
                if (args.length == 4) {
                    Path destino = salvarArquivo(e, Path.of(args[3]));
                    System.out.println();
                    System.out.println("Arquivo de licenca salvo em: " + destino);
                }
            } else {
                System.out.println("Uso:");
                System.out.println("  java ferramentas/GeradorLicenca.java                 (abre a janela)");
                System.out.println("  java ferramentas/GeradorLicenca.java chaves");
                System.out.println("  java ferramentas/GeradorLicenca.java gerar \"Nome do cliente\" XXXX-XXXX-XXXX-XXXX [pasta ou arquivo .lic]");
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

    /** Gera a chave de licença, registra no histórico e devolve a emissão. */
    static Emissao gerar(String cliente, String codigoMaquina) throws Exception {
        cliente = cliente == null ? "" : cliente.trim();
        String maquina = normalizarMaquina(codigoMaquina);
        if (cliente.isEmpty() || cliente.contains("\n") || cliente.contains("\r")) {
            throw new IllegalArgumentException("Nome do cliente invalido.");
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

        String payload = "cliente=" + cliente + "\nmaquina=" + maquina + "\nemitida=" + LocalDate.now();
        byte[] bytesPayload = payload.getBytes(StandardCharsets.UTF_8);
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initSign(privada);
        assinatura.update(bytesPayload);

        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String chave = PREFIXO + "." + b64.encodeToString(bytesPayload) + "." + b64.encodeToString(assinatura.sign());

        Emissao emissao = new Emissao(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), cliente, maquina, chave);
        registrar(emissao);
        return emissao;
    }

    /** Conteúdo do arquivo de licença: cabeçalho informativo + chave em linhas de 64 caracteres */
    static String conteudoArquivo(Emissao e) {
        StringBuilder texto = new StringBuilder();
        texto.append(INICIO_ARQUIVO).append("\r\n");
        texto.append("Cliente: ").append(e.cliente()).append("\r\n");
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

    /** Histórico em CSV (separador ;), uma licença por linha */
    private static void registrar(Emissao e) throws IOException {
        Path arquivo = arquivoHistorico();
        String linha = String.join(";", campoCsv(e.emitida().toString()), campoCsv(e.cliente()),
                campoCsv(e.maquina()), campoCsv(e.chave())) + "\r\n";
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
                lista.add(new Emissao(LocalDateTime.parse(c.get(0)), c.get(1), c.get(2), c.get(3)));
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
        private final JTextField maquina = new JTextField(20);
        private final JButton gerar = new JButton("Gerar chave");
        private final JTextArea chave = new JTextArea(4, 60);
        private final JButton copiar = new JButton("Copiar chave");
        private final JButton salvar = new JButton("Salvar arquivo de licença...");
        /** Licença exibida na tela (recém-gerada ou selecionada no histórico) */
        private Emissao atual;
        private Path ultimaPasta = Path.of(System.getProperty("user.home"), "Desktop");
        private final JLabel mensagem = new JLabel(" ");
        private final JTextField busca = new JTextField(20);
        private final DefaultTableModel modelo = new DefaultTableModel(new String[] {"Emitida em", "Cliente", "Máquina"}, 0) {
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
            painel.add(new JLabel("Código da máquina:"), g);
            g.gridx = 1;
            maquina.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            maquina.setToolTipText("Código mostrado na tela de ativação do cliente (XXXX-XXXX-XXXX-XXXX)");
            maquina.setMinimumSize(maquina.getPreferredSize());
            painel.add(maquina, g);

            g.gridx = 1; g.gridy = 2;
            gerar.addActionListener(e -> gerarChave());
            painel.add(gerar, g);

            g.gridx = 0; g.gridy = 3; g.anchor = GridBagConstraints.NORTHWEST;
            painel.add(new JLabel("Chave de licença:"), g);
            g.gridx = 1; g.fill = GridBagConstraints.BOTH; g.weighty = 1;
            chave.setEditable(false);
            chave.setLineWrap(true);
            chave.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            painel.add(new JScrollPane(chave), g);

            g.gridx = 1; g.gridy = 4; g.fill = GridBagConstraints.NONE; g.weighty = 0; g.anchor = GridBagConstraints.WEST;
            JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            copiar.setEnabled(false);
            copiar.addActionListener(e -> copiarChave());
            acoes.add(copiar);
            acoes.add(Box.createHorizontalStrut(8));
            salvar.setEnabled(false);
            salvar.addActionListener(e -> salvarArquivoLicenca());
            acoes.add(salvar);
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
            tabela.getColumnModel().getColumn(1).setPreferredWidth(260);
            tabela.getColumnModel().getColumn(2).setPreferredWidth(170);
            tabela.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) mostrarSelecionada();
            });
            JScrollPane rolagem = new JScrollPane(tabela);
            rolagem.setPreferredSize(new Dimension(560, 200));
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
                Emissao emissao = gerar(cliente.getText(), codigo);
                carregarHistorico();
                exibir(emissao);
                copiarChave();
                mensagem.setText("Chave gerada e copiada para " + emissao.cliente() + ".");
            } catch (Exception ex) {
                erro(ex);
            }
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
                modelo.addRow(new Object[] {DATA.format(e.emitida()), e.cliente(), e.maquina()});
            }
        }

        private void filtrar() {
            String texto = busca.getText().trim();
            ordenador.setRowFilter(texto.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 1, 2));
        }

        private void erro(Exception ex) {
            String texto = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            JOptionPane.showMessageDialog(this, texto, "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
