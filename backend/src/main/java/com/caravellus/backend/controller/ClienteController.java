package com.caravellus.backend.controller;

import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.service.ClienteService;
import com.caravellus.backend.service.DuplicidadeException;
import com.caravellus.backend.service.ImportacaoClientesService;
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
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        try {
            return ResponseEntity.status(201).body(service.salvar(cliente, confirmarDuplicidade, usuarioId, usuarioNome));
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
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        try {
            return ResponseEntity.ok(importacaoService.importar(
                    arquivo.getBytes(), arquivo.getOriginalFilename(), confirmar, usuarioId, usuarioNome));
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
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        try {
            return ResponseEntity.ok(service.atualizar(id, cliente, usuarioId, usuarioNome));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void excluir(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        service.excluir(id, usuarioId, usuarioNome);
    }
}
