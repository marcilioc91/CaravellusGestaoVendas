package com.caravellus.backend.service;

import com.caravellus.backend.entity.PerfilUsuario;
import com.caravellus.backend.entity.Usuario;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessões de login: o token é gerado no servidor e só ele sabe a qual usuário (e perfil) pertence.
 * Ficam em memória: o backend é reiniciado junto com o aplicativo, e aí todos entram de novo.
 */
@Service
public class SessaoService {

    /** Sessão sem uso por mais tempo que isso expira */
    static final Duration INATIVIDADE = Duration.ofHours(12);

    /** Usuário dono da sessão; o nome vai para a auditoria */
    public record UsuarioSessao(Long id, String nome, PerfilUsuario perfil) {
        public boolean isAdmin() {
            return perfil == PerfilUsuario.ADMIN;
        }
    }

    private static final class Sessao {
        final UsuarioSessao usuario;
        volatile Instant ultimoAcesso = Instant.now();

        Sessao(UsuarioSessao usuario) {
            this.usuario = usuario;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

    public String abrir(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String nome = usuario.getPessoa() != null && usuario.getPessoa().getNome() != null
                ? usuario.getPessoa().getNome()
                : usuario.getUsuarioLogin();
        sessoes.put(token, new Sessao(new UsuarioSessao(usuario.getId(), nome, usuario.getPerfil())));
        return token;
    }

    public Optional<UsuarioSessao> validar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Sessao sessao = sessoes.get(token);
        if (sessao == null) return Optional.empty();
        Instant agora = Instant.now();
        if (sessao.ultimoAcesso.plus(INATIVIDADE).isBefore(agora)) {
            sessoes.remove(token);
            return Optional.empty();
        }
        sessao.ultimoAcesso = agora;
        return Optional.of(sessao.usuario);
    }

    public void encerrar(String token) {
        if (token != null) sessoes.remove(token);
    }

    /** Perfil alterado ou senha redefinida: o usuário precisa entrar de novo */
    public void encerrarDoUsuario(Long usuarioId) {
        sessoes.values().removeIf(s -> s.usuario.id().equals(usuarioId));
    }
}
