package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "RECUPERACAO_SENHA")
@Data
public class RecuperacaoSenha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String codigoHash;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    private Integer tentativas = 0;

    @Column(nullable = false)
    private Boolean usado = false;
}
