package com.caravellus.backend.entity;

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
}
