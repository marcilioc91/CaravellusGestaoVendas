package com.caravellus.backend.controller;

import com.caravellus.backend.config.AutenticacaoInterceptor;
import com.caravellus.backend.entity.AuditoriaLog;
import com.caravellus.backend.service.AuditoriaService;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auditoria")
public class AuditoriaController {

    @Autowired
    private AuditoriaService service;

    @GetMapping
    public List<AuditoriaLog> listar(@RequestParam(required = false) Long usuarioId, @RequestAttribute(AutenticacaoInterceptor.USUARIO) UsuarioSessao usuario) {
        if (usuarioId != null) {
            return service.listarPorUsuario(usuarioId, usuario.evento());
        }
        return service.listar(usuario.evento());
    }
}
