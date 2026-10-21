package com.caravellus.backend.controller;

import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.service.ClienteService;
import com.caravellus.backend.service.DuplicidadeException;
import com.caravellus.backend.service.ImportacaoClientesService;
import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    @Autowired
    private ClienteService service;

    @Autowired
    private ImportacaoClientesService importacaoService;

    @GetMapping
    public List<Cliente> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<?> salvar(
            @RequestBody Cliente cliente,
            @RequestParam(defaultValue = "false") boolean confirmarDuplicidade,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.status(201).body(service.salvar(cliente, confirmarDuplicidade, usuario.id(), usuario.nome()));
        } catch (DuplicidadeException e) {
            return ResponseEntity.status(409).body(e.resposta());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    /** CSV de clientes: confirmar=false devolve só a prévia; confirmar=true grava os novos. */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(defaultValue = "false") boolean confirmar,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.ok(importacaoService.importar(
                    arquivo.getBytes(), arquivo.getOriginalFilename(), confirmar, usuario.id(), usuario.nome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(400).body("Não foi possível ler o arquivo.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Cliente cliente,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.ok(service.atualizar(id, cliente, usuario.id(), usuario.nome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void excluir(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        service.excluir(id, usuario.id(), usuario.nome());
    }
}
