package com.caravellus.backend.service;

import com.caravellus.backend.entity.RecuperacaoSenha;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.repository.RecuperacaoSenhaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    static final int MINUTOS_VALIDADE = 15;
    static final int MAX_TENTATIVAS = 5;
    static final int SEGUNDOS_ENTRE_ENVIOS = 60;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RecuperacaoSenhaRepository repository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private JavaMailSender mailSender;

    /** Login no servidor SMTP (no Brevo, diferente do e-mail do remetente). */
    @Value("${spring.mail.username:}")
    private String usuarioSmtp;

    @Value("${spring.mail.password:}")
    private String senhaSmtp;

    /** E-mail exibido como remetente; sem ele, usa o próprio login SMTP (ex.: Gmail). */
    @Value("${app.mail.remetente:${spring.mail.username:}}")
    private String remetente;

    @Value("${app.mail.remetente-nome:Caravellus}")
    private String remetenteNome;

    /** Gera um código e envia para o e-mail cadastrado. Usuário inexistente ou sem e-mail: IllegalArgumentException. */
    @Transactional
    public void solicitar(String identificador) {
        if (vazio(usuarioSmtp) || vazio(senhaSmtp) || vazio(remetente)) {
            throw new IllegalStateException("O envio de e-mail não está configurado. Procure o administrador do sistema.");
        }

        Usuario usuario =
                buscarUsuario(identificador).orElseThrow(() -> new IllegalArgumentException("Usuário ou e-mail não cadastrado."));
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            throw new IllegalArgumentException("Este usuário não possui e-mail cadastrado. Procure o administrador do sistema.");
        }

        LocalDateTime agora = LocalDateTime.now();
        boolean enviadoAgoraHaPouco = repository.findFirstByUsuarioAndUsadoFalseOrderByCriadoEmDesc(usuario)
                .map(r -> r.getCriadoEm().isAfter(agora.minusSeconds(SEGUNDOS_ENTRE_ENVIOS)))
                .orElse(false);
        if (enviadoAgoraHaPouco) return;

        // Só o código mais recente vale
        repository.findByUsuarioAndUsadoFalse(usuario).forEach(r -> r.setUsado(true));

        String codigo = String.format("%06d", random.nextInt(1_000_000));
        RecuperacaoSenha rec = new RecuperacaoSenha();
        rec.setUsuario(usuario);
        rec.setCodigoHash(passwordEncoder.encode(codigo));
        rec.setCriadoEm(agora);
        rec.setExpiraEm(agora.plusMinutes(MINUTOS_VALIDADE));
        repository.save(rec);

        enviarEmail(usuario, codigo);

        auditoriaService.registrar(usuario.getId(), nomeDe(usuario), "SOLICITACAO_RECUPERACAO_SENHA",
                "Código de recuperação de senha enviado para o e-mail cadastrado do usuário '" + usuario.getUsuarioLogin() + "'.");
    }

    /**
     * Confere o código sem consumi-lo (ele continua valendo para a redefinição).
     * Tentativas erradas ficam gravadas (sem rollback) e contam para o mesmo limite.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void validar(String identificador, String codigo) {
        conferirCodigo(identificador, codigo);
    }

    /** Valida o código e define a nova senha. Tentativas erradas ficam gravadas (sem rollback). */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void redefinir(String identificador, String codigo, String novaSenha) {
        if (novaSenha == null || novaSenha.isBlank()) {
            throw new IllegalArgumentException("A nova senha não pode ser vazia.");
        }

        RecuperacaoSenha rec = conferirCodigo(identificador, codigo);
        Usuario usuario = rec.getUsuario();

        rec.setUsado(true);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        auditoriaService.registrar(usuario.getId(), nomeDe(usuario), "RECUPERACAO_SENHA",
                "Senha do usuário '" + usuario.getUsuarioLogin() + "' redefinida via código enviado por e-mail.");
    }

    /** Retorna o código pendente se estiver correto; caso contrário conta a tentativa e lança IllegalArgumentException. */
    private RecuperacaoSenha conferirCodigo(String identificador, String codigo) {
        String erroPadrao = "Código inválido ou expirado.";

        Usuario usuario = buscarUsuario(identificador)
                .orElseThrow(() -> new IllegalArgumentException(erroPadrao));
        RecuperacaoSenha rec = repository.findFirstByUsuarioAndUsadoFalseOrderByCriadoEmDesc(usuario)
                .orElseThrow(() -> new IllegalArgumentException(erroPadrao));

        if (rec.getExpiraEm().isBefore(LocalDateTime.now()) || rec.getTentativas() >= MAX_TENTATIVAS) {
            rec.setUsado(true);
            throw new IllegalArgumentException(erroPadrao);
        }

        String codigoLimpo = codigo == null ? "" : codigo.replaceAll("\\D", "");
        if (!passwordEncoder.matches(codigoLimpo, rec.getCodigoHash())) {
            rec.setTentativas(rec.getTentativas() + 1);
            if (rec.getTentativas() >= MAX_TENTATIVAS) {
                rec.setUsado(true);
                throw new IllegalArgumentException("Código incorreto. Limite de tentativas atingido: solicite um novo código.");
            }
            throw new IllegalArgumentException("Código incorreto. Tentativas restantes: " + (MAX_TENTATIVAS - rec.getTentativas()) + ".");
        }
        return rec;
    }

    private Optional<Usuario> buscarUsuario(String identificador) {
        if (identificador == null || identificador.isBlank()) return Optional.empty();
        String valor = identificador.trim();
        return valor.contains("@")
                ? usuarioRepository.findByEmail(valor)
                : usuarioRepository.findByUsuarioLogin(valor);
    }

    private void enviarEmail(Usuario usuario, String codigo) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, "UTF-8");
            helper.setFrom(remetente, remetenteNome);
            helper.setTo(usuario.getEmail());
            helper.setSubject("Caravellus - Código de recuperação de senha");
            helper.setText("""
                    Olá, %s!

                    Recebemos um pedido para redefinir a senha do usuário "%s" no sistema Caravellus.

                    Seu código de verificação é: %s

                    O código é válido por %d minutos e só pode ser usado uma vez.
                    Se você não fez esse pedido, ignore este e-mail: sua senha continua a mesma.
                    """.formatted(nomeDe(usuario), usuario.getUsuarioLogin(), codigo, MINUTOS_VALIDADE));
            mailSender.send(mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de recuperação de senha para o usuário {}", usuario.getUsuarioLogin(), e);
            throw new IllegalStateException("Não foi possível enviar o e-mail. Tente novamente mais tarde.");
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String nomeDe(Usuario usuario) {
        return usuario.getPessoa() != null && usuario.getPessoa().getNome() != null
                ? usuario.getPessoa().getNome()
                : usuario.getUsuarioLogin();
    }
}
