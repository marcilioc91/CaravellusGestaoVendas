package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="CATEGORIA")
@Data

public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;

    /** Evento dono do registro; vem sempre da sessao (nunca do JSON enviado pelo frontend) */
    @JsonIgnore
    @Column(name = "EVENTO_CODIGO", nullable = false, updatable = false)
    private String eventoCodigo;
}
