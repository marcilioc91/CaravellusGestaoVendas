package com.caravellus.backend.controller;

import com.caravellus.backend.config.SomenteAdmin;
import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import com.caravellus.backend.dto.PagamentoRequestDTO;
import com.caravellus.backend.dto.VendaRequestDTO;
import com.caravellus.backend.entity.Venda;
import com.caravellus.backend.service.VendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/vendas")
public class VendaController {

    @Autowired
    private VendaService service;

    @PostMapping
    public ResponseEntity<?> realizarVenda(
            @RequestBody VendaRequestDTO dto,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.ok(service.realizarVenda(dto, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public List<Venda> listarTodas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        return service.listarPorPeriodo(inicio, fim, usuario.evento());
    }

    @GetMapping("/cliente/{clienteId}")
    public List<Venda> listarPorCliente(@PathVariable Long clienteId, @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        return service.listarPorCliente(clienteId, usuario.evento());
    }

    @PatchMapping("/{id}/pagamento")
    public Venda atualizarPagamento(
            @PathVariable Long id,
            @RequestBody PagamentoRequestDTO dto,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        return service.atualizarPagamento(id, dto, usuario);
    }

    @SomenteAdmin
    @GetMapping("/relatorio/inventario")
    public ResponseEntity<?> relatorioInventario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        return ResponseEntity.ok(service.gerarRelatorioInventario(inicio, fim, usuario.evento()));
    }

    @PutMapping("/{id}/itens")
    public ResponseEntity<?> atualizarItens(
            @PathVariable Long id,
            @RequestBody VendaRequestDTO dto,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        try {
            return ResponseEntity.ok(service.atualizarItens(id, dto, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @SomenteAdmin
    @DeleteMapping("/{id}")
    public void excluir(
            @PathVariable Long id,
            @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        service.excluir(id, usuario);
    }
}
