package com.caravellus.backend.controller;

import com.caravellus.backend.dto.AtualizarPerfilDTO;
import com.caravellus.backend.dto.CadastroRequestDTO;
import com.caravellus.backend.dto.ClienteExistenteDTO;
import com.caravellus.backend.dto.LoginRequestDTO;
import com.caravellus.backend.dto.RecuperacaoSenhaDTO;
import com.caravellus.backend.dto.ResetSenhaDTO;
import com.caravellus.backend.dto.TrocarSenhaDTO;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.service.DuplicidadeException;
import com.caravellus.backend.service.RecuperacaoSenhaService;
import com.caravellus.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class UsuarioController {
    @Autowired
    private UsuarioService service;

    @Autowired
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequest) {
        Usuario encontrado = service.autenticar(loginRequest.getLogin(), loginRequest.getSenha());
        if (encontrado != null) {
            return ResponseEntity.ok(encontrado);
        }
        return ResponseEntity.status(401).body("Login ou senha inválidos");
    }

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

    @PostMapping("/trocar-senha")
    public ResponseEntity<?> trocarSenha(@RequestBody TrocarSenhaDTO dto) {
        try {
            return ResponseEntity.ok(service.trocarSenha(dto.getLogin(), dto.getSenhaAtual(), dto.getNovaSenha()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

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

    @PostMapping("/recuperar-senha/validar")
    public ResponseEntity<?> validarCodigoRecuperacao(@RequestBody RecuperacaoSenhaDTO dto) {
        try {
            recuperacaoSenhaService.validar(dto.getIdentificador(), dto.getCodigo());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PostMapping("/recuperar-senha/redefinir")
    public ResponseEntity<?> redefinirSenha(@RequestBody RecuperacaoSenhaDTO dto) {
        try {
            recuperacaoSenhaService.redefinir(dto.getIdentificador(), dto.getCodigo(), dto.getNovaSenha());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios(
            @RequestHeader(value = "X-Usuario-Perfil", required = false) String perfil) {
        if (!"ADMIN".equals(perfil)) {
            return ResponseEntity.status(403).body("Acesso negado.");
        }
        return ResponseEntity.ok(service.listarTodos());
    }

    @PatchMapping("/usuarios/{id}/reset-senha")
    public ResponseEntity<?> resetSenha(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Perfil", required = false) String perfil,
            @RequestBody ResetSenhaDTO dto) {
        if (!"ADMIN".equals(perfil)) {
            return ResponseEntity.status(403).body("Acesso negado.");
        }
        try {
            service.resetSenha(id, dto.getNovaSenha());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PatchMapping("/usuarios/{id}/perfil")
    public ResponseEntity<?> atualizarPerfil(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Perfil", required = false) String perfil,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long adminId,
            @RequestBody AtualizarPerfilDTO dto) {
        if (!"ADMIN".equals(perfil)) {
            return ResponseEntity.status(403).body("Acesso negado.");
        }
        if (id.equals(adminId)) {
            return ResponseEntity.status(400).body("Você não pode alterar o próprio perfil.");
        }
        try {
            return ResponseEntity.ok(service.atualizarPerfil(id, dto.getPerfil()));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
