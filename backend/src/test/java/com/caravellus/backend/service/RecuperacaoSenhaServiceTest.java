package com.caravellus.backend.service;

import com.caravellus.backend.IntegracaoTest;
import com.caravellus.backend.entity.PerfilUsuario;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.entity.RecuperacaoSenha;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.RecuperacaoSenhaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fluxo "Esqueci minha senha": código de 6 dígitos por e-mail (envio simulado), guardado só como hash,
 * de uso único, com limite de tentativas e intervalo mínimo entre envios.
 */
class RecuperacaoSenhaServiceTest extends IntegracaoTest {

    @Autowired private RecuperacaoSenhaService service;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PessoaRepository pessoaRepository;
    @Autowired private RecuperacaoSenhaRepository recuperacaoRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        usuario = novoUsuario();
    }

    @Test
    void codigoEnviadoPorEmailRedefineASenhaEFicaInutilizado() throws Exception {
        service.solicitar(usuario.getEmail());
        String codigo = codigoDoEmail();

        RecuperacaoSenha pendente = pendente();
        assertNotEquals(codigo, pendente.getCodigoHash(), "o banco guarda só o hash");
        assertTrue(passwordEncoder.matches(codigo, pendente.getCodigoHash()));

        service.validar(usuario.getUsuarioLogin(), codigo); // conferir não consome o código
        service.redefinir(usuario.getUsuarioLogin(), codigo, "novaSenha123");

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("novaSenha123", atualizado.getSenha()));
        assertThrows(IllegalArgumentException.class,
                () -> service.redefinir(usuario.getUsuarioLogin(), codigo, "outraSenha123"), "uso único");
    }

    @Test
    void tentativasErradasFicamGravadasEBloqueiamNoLimite() throws Exception {
        service.solicitar(usuario.getUsuarioLogin());
        String codigo = codigoDoEmail();
        String errado = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < RecuperacaoSenhaService.MAX_TENTATIVAS; i++) {
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> service.validar(usuario.getUsuarioLogin(), errado));
            assertTrue(erro.getMessage().contains("Tentativas restantes"), erro.getMessage());
        }
        assertEquals(RecuperacaoSenhaService.MAX_TENTATIVAS - 1, pendente().getTentativas(),
                "a tentativa errada é gravada mesmo com a exceção (sem rollback)");

        IllegalArgumentException ultima = assertThrows(IllegalArgumentException.class,
                () -> service.validar(usuario.getUsuarioLogin(), errado));
        assertTrue(ultima.getMessage().contains("Limite de tentativas"), ultima.getMessage());

        // Depois do limite, nem o código certo vale
        assertThrows(IllegalArgumentException.class,
                () -> service.redefinir(usuario.getUsuarioLogin(), codigo, "novaSenha123"));
    }

    @Test
    void novoPedidoLogoEmSeguidaNaoReenviaEmail() {
        service.solicitar(usuario.getEmail());
        service.solicitar(usuario.getEmail());

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void usuarioInexistenteERecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar("ninguem-" + UUID.randomUUID()));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void senhaCurtaERecusadaAntesDeConferirOCodigo() throws Exception {
        service.solicitar(usuario.getEmail());
        String codigo = codigoDoEmail();

        assertThrows(IllegalArgumentException.class, () -> service.redefinir(usuario.getUsuarioLogin(), codigo, "123"));
        assertEquals(0, pendente().getTentativas(), "senha inválida não gasta tentativa");
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    private String codigoDoEmail() throws Exception {
        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        assertEquals(usuario.getEmail(), enviado.getValue().getAllRecipients()[0].toString());

        Matcher m = Pattern.compile("código de verificação é: (\\d{6})").matcher(enviado.getValue().getContent().toString());
        assertTrue(m.find(), "o e-mail traz o código");
        return m.group(1);
    }

    private RecuperacaoSenha pendente() {
        return recuperacaoRepository.findFirstByUsuarioAndUsadoFalseOrderByCriadoEmDesc(usuario).orElseThrow();
    }

    private Usuario novoUsuario() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Pessoa pessoa = new Pessoa();
        pessoa.setNome("USUARIO TESTE " + sufixo);

        Usuario u = new Usuario();
        u.setPessoa(pessoaRepository.save(pessoa));
        u.setUsuarioLogin("teste" + sufixo);
        u.setEmail("teste" + sufixo + "@caravellus.local");
        u.setSenha(passwordEncoder.encode("senhaAntiga1"));
        u.setPerfil(PerfilUsuario.OPERADOR);
        return usuarioRepository.save(u);
    }
}
