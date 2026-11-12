package com.caravellus.backend.controller;

import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.config.Publico;
import com.caravellus.backend.config.SomenteAdmin;
import com.caravellus.backend.entity.Evento;
import com.caravellus.backend.dto.ResumoEventoDTO;
import com.caravellus.backend.service.EventoService;
import com.caravellus.backend.service.SessaoService;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    @Autowired
    private EventoService service;

    @Autowired
    private SessaoService sessaoService;

    /** Lista da tela de login (antes de entrar no sistema) */
    @Publico
    @GetMapping("/ativos")
    public List<Evento> listarAtivos() {
        return service.listarAtivos();
    }

    @SomenteAdmin
    @GetMapping
    public List<Evento> listarTodos() {
        return service.listarTodos();
    }

    @SomenteAdmin
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody Evento evento,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.status(201).body(service.criar(evento, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    /** Quanto será apagado: a tela mostra no aviso antes de confirmar */
    @SomenteAdmin
    @GetMapping("/{codigo}/resumo")
    public ResponseEntity<?> resumo(@PathVariable String codigo) {
        try {
            return ResponseEntity.ok(service.resumir(codigo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /** Exclui o evento com todos os dados; devolve o que foi apagado */
    @SomenteAdmin
    @DeleteMapping("/{codigo}")
    public ResponseEntity<?> excluir(
            @PathVariable String codigo,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            ResumoEventoDTO excluido = service.excluir(codigo, usuario);
            sessaoService.encerrarDoEvento(excluido.codigo());
            return ResponseEntity.ok(excluido);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @SomenteAdmin
    @PutMapping("/{codigo}")
    public ResponseEntity<?> atualizar(
            @PathVariable String codigo,
            @RequestBody Evento evento,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.ok(service.atualizar(codigo, evento, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
