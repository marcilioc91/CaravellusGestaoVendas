package com.caravellus.backend.controller;

import com.caravellus.backend.dto.AtivacaoLicencaDTO;
import com.caravellus.backend.dto.LicencaStatusDTO;
import com.caravellus.backend.dto.SolicitacaoLicencaDTO;
import com.caravellus.backend.service.LicencaService;
import com.caravellus.backend.service.SolicitacaoLicencaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Únicos endpoints liberados enquanto o sistema não está ativado (ver LicencaInterceptor). */
@RestController
@RequestMapping("/licenca")
public class LicencaController {

    @Autowired
    private LicencaService service;

    @Autowired
    private SolicitacaoLicencaService solicitacaoService;

    @GetMapping("/status")
    public LicencaStatusDTO status() {
        return LicencaStatusDTO.de(service, solicitacaoService);
    }

    /** Primeiro acesso: envia ao fornecedor o pedido com o código desta máquina */
    @PostMapping("/solicitar")
    public ResponseEntity<?> solicitar(@RequestBody SolicitacaoLicencaDTO dto) {
        try {
            solicitacaoService.solicitar(dto.getNome(), dto.getEmail(), dto.getTelefone());
            return ResponseEntity.ok(LicencaStatusDTO.de(service, solicitacaoService));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    @PostMapping("/ativar")
    public ResponseEntity<?> ativar(@RequestBody AtivacaoLicencaDTO dto) {
        try {
            service.ativar(dto.getChave());
            return ResponseEntity.ok(LicencaStatusDTO.de(service, solicitacaoService));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
