package com.caravellus.backend.controller;

import com.caravellus.backend.config.SomenteAdmin;
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
    public Venda realizarVenda(@RequestBody VendaRequestDTO dto) {
        return service.realizarVenda(dto);
    }

    @GetMapping
    public List<Venda> listarTodas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.listarPorPeriodo(inicio, fim);
    }

    @GetMapping("/cliente/{clienteId}")
    public List<Venda> listarPorCliente(@PathVariable Long clienteId) {
        return service.listarPorCliente(clienteId);
    }

    @PatchMapping("/{id}/pagamento")
    public Venda atualizarPagamento(@PathVariable Long id, @RequestBody PagamentoRequestDTO dto) {
        return service.atualizarPagamento(id, dto);
    }

    @SomenteAdmin
    @GetMapping("/relatorio/inventario")
    public ResponseEntity<?> relatorioInventario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(service.gerarRelatorioInventario(inicio, fim));
    }

    @PutMapping("/{id}/itens")
    public ResponseEntity<Venda> atualizarItens(@PathVariable Long id, @RequestBody VendaRequestDTO dto) {
        Venda vendaAtualizada = service.atualizarItens(id, dto);
        return ResponseEntity.ok(vendaAtualizada);
    }
}
