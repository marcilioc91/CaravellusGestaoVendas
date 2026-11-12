package com.caravellus.backend.controller;

import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.config.Publico;
import com.caravellus.backend.config.SomenteAdmin;
import com.caravellus.backend.dto.AtualizarPerfilDTO;
import com.caravellus.backend.dto.CadastroRequestDTO;
import com.caravellus.backend.dto.ClienteExistenteDTO;
import com.caravellus.backend.dto.LoginRequestDTO;
import com.caravellus.backend.dto.RecuperacaoSenhaDTO;
import com.caravellus.backend.dto.ResetSenhaDTO;
import com.caravellus.backend.dto.SessaoDTO;
import com.caravellus.backend.entity.Evento;
import com.caravellus.backend.dto.TrocarSenhaDTO;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.service.DuplicidadeException;
import com.caravellus.backend.service.RecuperacaoSenhaService;
import com.caravellus.backend.service.EventoService;
import com.caravellus.backend.service.UltimoAcessoService;
import com.caravellus.backend.service.SessaoService;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import com.caravellus.backend.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class UsuarioController {
    @Autowired
    private UsuarioService service;

    @Autowired
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private EventoService eventoService;

    @Autowired
    private UltimoAcessoService ultimoAcessoService;

    /**
     * Com trocarSenha pendente não abre sessão: o token só vem depois da troca (/trocar-senha).
     * A sessão abre no evento escolhido, que precisa estar ativo.
     */
    @Publico
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequest) {
        Usuario encontrado = service.autenticar(loginRequest.getLogin(), loginRequest.getSenha());
        if (encontrado == null) {
            return ResponseEntity.status(401).body("Login ou senha inválidos");
        }
        Evento evento;
        try {
            evento = eventoService.buscarAtivo(loginRequest.getEvento());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
        String token = Boolean.TRUE.equals(encontrado.getTrocarSenha()) ? null : sessaoService.abrir(encontrado, evento.getCodigo());
        ultimoAcessoService.registrar(encontrado.getUsuarioLogin(), evento.getCodigo());
        return ResponseEntity.ok(new SessaoDTO(encontrado, token, evento));
    }

    /** Último usuário e evento usados (ultimo-acesso.ini): a tela de login abre com eles preenchidos */
    @Publico
    @GetMapping("/ultimo-acesso")
    public Map<String, String> ultimoAcesso() {
        return ultimoAcessoService.obter();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        sessaoService.encerrar(AutenticacaoInterceptor.token(request));
        return ResponseEntity.noContent().build();
    }

    /** Cadastro aberto na tela de login: sempre cria OPERADOR (o ADMIN promove em Gerenciar usuários). */
    @Publico
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody CadastroRequestDTO dto) {
        try {
            Usuario criado = service.cadastrar(dto);
            return ResponseEntity.status(201).body(criado);
        } catch (DuplicidadeException e) {
            return ResponseEntity.status(409).body(e.resposta());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Erro ao cadastrar: " + e.getMessage());
        }
    }

    /** Preenchimento do cadastro: cliente que já tem esse CPF (404 se o CPF é novo, 409 se já é usuário). */
    @Publico
    @GetMapping("/cadastro/cliente-por-cpf/{cpf}")
    public ResponseEntity<?> clientePorCpf(@PathVariable String cpf) {
        try {
            return service.buscarPessoaParaPromover(cpf)
                    .<ResponseEntity<?>>map(p -> ResponseEntity.ok(new ClienteExistenteDTO(p.getNome(), p.getTelefone())))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @Publico
    @PostMapping("/trocar-senha")
    public ResponseEntity<?> trocarSenha(@RequestBody TrocarSenhaDTO dto) {
        try {
            Evento evento = eventoService.buscarAtivo(dto.getEvento());
            Usuario usuario = service.trocarSenha(dto.getLogin(), dto.getSenhaAtual(), dto.getNovaSenha());
            ultimoAcessoService.registrar(usuario.getUsuarioLogin(), evento.getCodigo());
            return ResponseEntity.ok(new SessaoDTO(usuario, sessaoService.abrir(usuario, evento.getCodigo()), evento));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @Publico
    @PostMapping("/recuperar-senha/solicitar")
    public ResponseEntity<?> solicitarRecuperacao(@RequestBody RecuperacaoSenhaDTO dto) {
        try {
            recuperacaoSenhaService.solicitar(dto.getIdentificador());
            return ResponseEntity.ok("Código enviado para o e-mail cadastrado.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    @Publico
    @PostMapping("/recuperar-senha/validar")
    public ResponseEntity<?> validarCodigoRecuperacao(@RequestBody RecuperacaoSenhaDTO dto) {
        try {
            recuperacaoSenhaService.validar(dto.getIdentificador(), dto.getCodigo());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @Publico
    @PostMapping("/recuperar-senha/redefinir")
    public ResponseEntity<?> redefinirSenha(@RequestBody RecuperacaoSenhaDTO dto) {
        try {
            recuperacaoSenhaService.redefinir(dto.getIdentificador(), dto.getCodigo(), dto.getNovaSenha());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @SomenteAdmin
    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @SomenteAdmin
    @PatchMapping("/usuarios/{id}/reset-senha")
    public ResponseEntity<?> resetSenha(@PathVariable Long id, @RequestBody ResetSenhaDTO dto) {
        try {
            service.resetSenha(id, dto.getNovaSenha());
            sessaoService.encerrarDoUsuario(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @SomenteAdmin
    @PatchMapping("/usuarios/{id}/perfil")
    public ResponseEntity<?> atualizarPerfil(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao admin,
            @RequestBody AtualizarPerfilDTO dto) {
        if (id.equals(admin.id())) {
            return ResponseEntity.status(400).body("Você não pode alterar o próprio perfil.");
        }
        try {
            Usuario atualizado = service.atualizarPerfil(id, dto.getPerfil());
            sessaoService.encerrarDoUsuario(id);
            return ResponseEntity.ok(atualizado);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
