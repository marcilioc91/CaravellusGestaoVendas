package com.caravellus.backend.controller;

import com.caravellus.backend.entity.AuditoriaLog;
import com.caravellus.backend.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auditoria")
public class AuditoriaController {

    @Autowired
    private AuditoriaService service;

    @GetMapping
    public List<AuditoriaLog> listar(@RequestParam(required = false) Long usuarioId) {
        if (usuarioId != null) {
            return service.listarPorUsuario(usuarioId);
        }
        return service.listar();
    }
}
