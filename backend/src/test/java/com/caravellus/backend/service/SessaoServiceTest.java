package com.caravellus.backend.service;

import com.caravellus.backend.entity.PerfilUsuario;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sessões de login no servidor: o token é a única coisa que o cliente guarda. */
class SessaoServiceTest {

    private final SessaoService sessoes = new SessaoService();

    @Test
    void tokenAbertoIdentificaUsuarioEPerfil() {
        String token = sessoes.abrir(usuario(1L, "Maria", PerfilUsuario.ADMIN));

        UsuarioSessao sessao = sessoes.validar(token).orElseThrow();
        assertEquals(1L, sessao.id());
        assertEquals("Maria", sessao.nome());
        assertTrue(sessao.isAdmin());
    }

    @Test
    void tokensSaoAleatoriosEImprevisiveis() {
        Usuario u = usuario(1L, "Maria", PerfilUsuario.OPERADOR);
        String a = sessoes.abrir(u);
        String b = sessoes.abrir(u);

        assertNotEquals(a, b);
        assertEquals(43, a.length(), "32 bytes em Base64 URL sem padding");
        assertFalse(sessoes.validar(a).orElseThrow().isAdmin());
    }

    @Test
    void tokenDesconhecidoOuVazioNaoValida() {
        assertTrue(sessoes.validar(null).isEmpty());
        assertTrue(sessoes.validar(" ").isEmpty());
        assertTrue(sessoes.validar("token-inventado").isEmpty());
    }

    @Test
    void logoutEncerraSoAquelaSessao() {
        Usuario u = usuario(1L, "Maria", PerfilUsuario.OPERADOR);
        String pc = sessoes.abrir(u);
        String notebook = sessoes.abrir(u);

        sessoes.encerrar(pc);

        assertTrue(sessoes.validar(pc).isEmpty());
        assertTrue(sessoes.validar(notebook).isPresent());
    }

    @Test
    void trocaDePerfilOuSenhaDerrubaTodasAsSessoesDoUsuario() {
        String maria1 = sessoes.abrir(usuario(1L, "Maria", PerfilUsuario.OPERADOR));
        String maria2 = sessoes.abrir(usuario(1L, "Maria", PerfilUsuario.OPERADOR));
        String joao = sessoes.abrir(usuario(2L, "João", PerfilUsuario.OPERADOR));

        sessoes.encerrarDoUsuario(1L);

        assertTrue(sessoes.validar(maria1).isEmpty());
        assertTrue(sessoes.validar(maria2).isEmpty());
        assertTrue(sessoes.validar(joao).isPresent());
    }

    @Test
    void semNomeDePessoaUsaOLogin() {
        Usuario u = usuario(3L, null, PerfilUsuario.OPERADOR);
        assertEquals("login3", sessoes.validar(sessoes.abrir(u)).orElseThrow().nome());
    }

    private static Usuario usuario(Long id, String nome, PerfilUsuario perfil) {
        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nome);
        Usuario u = new Usuario();
        u.setId(id);
        u.setPessoa(pessoa);
        u.setUsuarioLogin("login" + id);
        u.setPerfil(perfil);
        return u;
    }
}
