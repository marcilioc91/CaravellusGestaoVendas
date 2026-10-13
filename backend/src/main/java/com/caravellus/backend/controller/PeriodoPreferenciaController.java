package com.caravellus.backend.controller;

import com.caravellus.backend.service.PeriodoPreferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/preferencias/periodo")
public class PeriodoPreferenciaController {

    private static final Set<String> TELAS = Set.of("relatorio", "historico");

    @Autowired
    private PeriodoPreferenciaService service;

    @GetMapping("/{tela}")
    public ResponseEntity<?> obter(@PathVariable String tela) {
        if (!TELAS.contains(tela)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.obter(tela));
    }

    @PutMapping("/{tela}")
    public ResponseEntity<?> salvar(@PathVariable String tela, @RequestBody Map<String, String> corpo) {
        if (!TELAS.contains(tela)) return ResponseEntity.notFound().build();
        try {
            service.salvar(tela, LocalDate.parse(corpo.get("inicio")), LocalDate.parse(corpo.get("fim")));
        } catch (DateTimeParseException | NullPointerException e) {
            return ResponseEntity.badRequest().body("Periodo invalido.");
        }
        return ResponseEntity.noContent().build();
    }
}
