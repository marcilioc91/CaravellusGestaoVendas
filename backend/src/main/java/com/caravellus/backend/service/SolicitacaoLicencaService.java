package com.caravellus.backend.service;

import com.caravellus.backend.util.ValidacaoUtil;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

/**
 * Pedido de licença feito no primeiro acesso: o cliente informa nome e e-mail na tela de ativação
 * e o sistema envia ao fornecedor (app.licenca.email-fornecedor) um e-mail com o código da máquina.
 * O fornecedor gera a licença no GeradorLicenca, que a envia para o e-mail do cliente.
 *
 * O último pedido fica em solicitacao-licenca.properties, na pasta de dados, para a tela de
 * ativação mostrar que ele já foi enviado (e para onde) mesmo depois de reiniciar o sistema.
 */
@Service
public class SolicitacaoLicencaService {

    private static final Logger log = LoggerFactory.getLogger(SolicitacaoLicencaService.class);

    static final String NOME_ARQUIVO = "solicitacao-licenca.properties";
    static final int SEGUNDOS_ENTRE_ENVIOS = 60;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Último pedido enviado desta máquina */
    public record Solicitacao(String nome, String email, String telefone, LocalDateTime enviadaEm) {}

    @Autowired
    private LicencaService licencaService;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.licenca.email-fornecedor:portocabral.sistemas@gmail.com}")
    private String emailFornecedor;

    @Value("${spring.mail.username:}")
    private String usuarioSmtp;

    @Value("${spring.mail.password:}")
    private String senhaSmtp;

    @Value("${app.mail.remetente:${spring.mail.username:}}")
    private String remetente;

    @Value("${app.mail.remetente-nome:Caravellus}")
    private String remetenteNome;

    private final Path arquivo;
    private volatile Solicitacao ultima;

    public SolicitacaoLicencaService(@Value("${app.dados.dir}") String pastaDados) {
        this.arquivo = Path.of(pastaDados).resolve(NOME_ARQUIVO);
    }

    @PostConstruct
    void carregar() {
        if (!Files.exists(arquivo)) return;
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            Properties p = new Properties();
            p.load(entrada);
            ultima = new Solicitacao(p.getProperty("nome", ""), p.getProperty("email", ""),
                    p.getProperty("telefone", ""), LocalDateTime.parse(p.getProperty("enviadaEm")));
        } catch (Exception e) {
            log.warn("Não foi possível ler {}: {}", arquivo, e.getMessage());
        }
    }

    public Solicitacao getUltima() {
        return ultima;
    }

    /** Envia o pedido ao fornecedor. Dados inválidos: IllegalArgumentException; envio impossível: IllegalStateException. */
    public synchronized Solicitacao solicitar(String nome, String email, String telefone) {
        nome = limpar(nome);
        email = limpar(email);
        telefone = limpar(telefone);
        if (licencaService.isAtiva()) {
            throw new IllegalArgumentException("Este sistema já está ativado.");
        }
        if (licencaService.getCodigoMaquina() == null) {
            throw new IllegalStateException(licencaService.getProblema());
        }
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Informe o nome do cliente ou da empresa.");
        }
        if (!ValidacaoUtil.emailValido(email)) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        if (ultima != null && ultima.enviadaEm().isAfter(LocalDateTime.now().minusSeconds(SEGUNDOS_ENTRE_ENVIOS))) {
            throw new IllegalArgumentException("O pedido acabou de ser enviado. Aguarde um minuto para enviar de novo.");
        }
        if (vazio(usuarioSmtp) || vazio(senhaSmtp) || vazio(remetente)) {
            throw new IllegalStateException("O envio de e-mail não está configurado. Envie o código da máquina ao fornecedor.");
        }

        Solicitacao nova = new Solicitacao(nome, email, telefone, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        enviarEmail(nova, licencaService.getCodigoMaquina());
        ultima = nova;
        gravar(nova);
        log.info("Pedido de licença enviado ao fornecedor para '{}' <{}>.", nome, email);
        return nova;
    }

    private void enviarEmail(Solicitacao s, String codigoMaquina) {
        String computador;
        try {
            computador = InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            computador = "(desconhecido)";
        }
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, "UTF-8");
            helper.setFrom(remetente, remetenteNome);
            helper.setTo(emailFornecedor);
            helper.setReplyTo(s.email());
            helper.setSubject("Caravellus - Pedido de licença: " + s.nome());
            helper.setText("""
                    Novo pedido de licença do Caravellus.

                    Cliente: %s
                    E-mail: %s
                    Telefone: %s
                    Código da máquina: %s
                    Computador: %s
                    Pedido em: %s

                    Para gerar e enviar a licença, abra o gerador (gerar-licenca.bat) e preencha
                    cliente, e-mail e código da máquina com os dados acima, ou rode:

                    gerar-licenca.bat "%s" %s %s
                    """.formatted(s.nome(), s.email(), s.telefone().isEmpty() ? "(não informado)" : s.telefone(),
                    codigoMaquina, computador, DATA_HORA.format(s.enviadaEm()),
                    s.nome().replace("\"", "'"), codigoMaquina, s.email()));
            mailSender.send(mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar o pedido de licença ao fornecedor", e);
            throw new IllegalStateException("Não foi possível enviar o pedido. Verifique a conexão com a internet "
                    + "ou envie o código da máquina ao fornecedor.");
        }
    }

    /** Properties.store(OutputStream) grava acentos como escapes Unicode: o arquivo fica em ASCII */
    private void gravar(Solicitacao s) {
        Properties p = new Properties();
        p.setProperty("nome", s.nome());
        p.setProperty("email", s.email());
        p.setProperty("telefone", s.telefone());
        p.setProperty("enviadaEm", s.enviadaEm().toString());
        try {
            Files.createDirectories(arquivo.getParent());
            try (OutputStream saida = Files.newOutputStream(arquivo)) {
                p.store(saida, "Ultimo pedido de licenca enviado ao fornecedor");
            }
        } catch (IOException e) {
            // O pedido já foi enviado: só a tela deixa de lembrar dele depois de reiniciar
            log.warn("Não foi possível gravar {}: {}", arquivo, e.getMessage());
        }
    }

    private static String limpar(String valor) {
        return valor == null ? "" : valor.replaceAll("[\\r\\n]", " ").trim();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
