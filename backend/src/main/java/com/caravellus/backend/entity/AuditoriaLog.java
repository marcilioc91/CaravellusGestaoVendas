package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUDITORIA_LOG")
@Data
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private String usuarioNome;
    private String tipoOperacao;

    @Column(length = 500)
    private String descricao;

    private LocalDateTime dataHora;

    /** null: operacao fora de um evento (troca e recuperacao de senha, cadastro de usuario) */
    @JsonIgnore
    @Column(name = "EVENTO_CODIGO", updatable = false)
    private String eventoCodigo;
}
