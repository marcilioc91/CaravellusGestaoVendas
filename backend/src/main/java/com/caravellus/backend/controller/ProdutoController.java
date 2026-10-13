package com.caravellus.backend.controller;

import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.service.ProdutoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService service;

    @GetMapping
    public List<Produto> listar() {
        return service.listar();
    }

    @PostMapping
    public Produto salvar(
            @RequestBody Produto produto,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        return service.salvar(produto, usuarioId, usuarioNome);
    }

    @PutMapping("/{id}")
    public Produto atualizar(
            @PathVariable Long id,
            @RequestBody Produto produto,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        produto.setId(id);
        return service.salvar(produto, usuarioId, usuarioNome);
    }

    @DeleteMapping("/{id}")
    public void excluir(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        service.excluir(id, usuarioId, usuarioNome);
    }

    /** O frontend pede a imagem com ?v=imagemVersao, então a resposta pode ficar no cache do navegador */
    @GetMapping("/{id}/imagem")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        return service.buscarImagem(id)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getTipo()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)))
                        .body(img.getConteudo()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void salvarImagem(
            @PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) throws IOException {
        service.salvarImagem(id, arquivo.getContentType(), arquivo.getBytes(), usuarioId, usuarioNome);
    }

    @DeleteMapping("/{id}/imagem")
    public void removerImagem(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long usuarioId,
            @RequestHeader(value = "X-Usuario-Nome", required = false) String usuarioNome) {
        service.removerImagem(id, usuarioId, usuarioNome);
    }
}
