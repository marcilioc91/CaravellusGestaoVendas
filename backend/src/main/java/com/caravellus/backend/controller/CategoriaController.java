package com.caravellus.backend.controller;

import com.caravellus.backend.entity.Categoria;
import com.caravellus.backend.service.CategoriaService;
import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaService service;

    @GetMapping
    public List<Categoria> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<?> salvar(
            @RequestBody Categoria categoria,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            categoria.setId(null);
            return ResponseEntity.status(201).body(service.salvar(categoria, usuario.id(), usuario.nome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Categoria categoria,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            categoria.setId(id);
            return ResponseEntity.ok(service.salvar(categoria, usuario.id(), usuario.nome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            service.excluir(id, usuario.id(), usuario.nome());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
