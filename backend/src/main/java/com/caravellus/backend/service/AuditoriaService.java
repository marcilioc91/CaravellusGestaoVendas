package com.caravellus.backend.service;

import com.caravellus.backend.entity.AuditoriaLog;
import com.caravellus.backend.repository.AuditoriaLogRepository;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaLogRepository repository;

    /** Operação feita dentro do evento da sessão */
    public void registrar(UsuarioSessao usuario, String tipoOperacao, String descricao) {
        gravar(usuario.id(), usuario.nome(), usuario.evento(), tipoOperacao, descricao);
    }

    /** Operação fora de um evento (senha, cadastro de usuário, cadastro de eventos) */
    public void registrar(Long usuarioId, String usuarioNome, String tipoOperacao, String descricao) {
        gravar(usuarioId, usuarioNome, null, tipoOperacao, descricao);
    }

    private void gravar(Long usuarioId, String usuarioNome, String evento, String tipoOperacao, String descricao) {
        AuditoriaLog log = new AuditoriaLog();
        log.setEventoCodigo(evento);
        log.setUsuarioId(usuarioId);
        log.setUsuarioNome(usuarioNome != null ? usuarioNome : "Desconhecido");
        log.setTipoOperacao(tipoOperacao);
        log.setDescricao(descricao);
        log.setDataHora(LocalDateTime.now());
        repository.save(log);
    }

    /** Operações do evento e as que não pertencem a nenhum evento */
    public List<AuditoriaLog> listar(String evento) {
        return repository.listarDoEvento(evento);
    }

    public List<AuditoriaLog> listarPorUsuario(Long usuarioId, String evento) {
        return repository.listarDoEventoPorUsuario(evento, usuarioId);
    }
}
