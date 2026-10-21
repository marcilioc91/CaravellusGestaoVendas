package com.caravellus.backend.controller;

import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.service.ProdutoService;
import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.config.Publico;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
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
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        return service.salvar(produto, usuario.id(), usuario.nome());
    }

    @PutMapping("/{id}")
    public Produto atualizar(
            @PathVariable Long id,
            @RequestBody Produto produto,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        produto.setId(id);
        return service.salvar(produto, usuario.id(), usuario.nome());
    }

    @DeleteMapping("/{id}")
    public void excluir(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        service.excluir(id, usuario.id(), usuario.nome());
    }

    /**
     * O frontend pede a imagem com ?v=imagemVersao, então a resposta pode ficar no cache do navegador.
     * Pública: a tag <img> não envia o token de login.
     */
    @Publico
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
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) throws IOException {
        service.salvarImagem(id, arquivo.getContentType(), arquivo.getBytes(), usuario.id(), usuario.nome());
    }

    @DeleteMapping("/{id}/imagem")
    public void removerImagem(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        service.removerImagem(id, usuario.id(), usuario.nome());
    }
}
