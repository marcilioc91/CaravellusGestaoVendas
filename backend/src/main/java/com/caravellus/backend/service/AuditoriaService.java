package com.caravellus.backend.service;

import com.caravellus.backend.entity.AuditoriaLog;
import com.caravellus.backend.repository.AuditoriaLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaLogRepository repository;

    public void registrar(Long usuarioId, String usuarioNome, String tipoOperacao, String descricao) {
        AuditoriaLog log = new AuditoriaLog();
        log.setUsuarioId(usuarioId);
        log.setUsuarioNome(usuarioNome != null ? usuarioNome : "Desconhecido");
        log.setTipoOperacao(tipoOperacao);
        log.setDescricao(descricao);
        log.setDataHora(LocalDateTime.now());
        repository.save(log);
    }

    public List<AuditoriaLog> listar() {
        return repository.findAllByOrderByDataHoraDesc();
    }

    public List<AuditoriaLog> listarPorUsuario(Long usuarioId) {
        return repository.findByUsuarioIdOrderByDataHoraDesc(usuarioId);
    }
}
