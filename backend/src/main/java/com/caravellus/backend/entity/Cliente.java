package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="CLIENTE")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** A mesma pessoa pode ser cliente em varios eventos (uma vez em cada) */
    @ManyToOne
    @JoinColumn(name = "PESSOA_ID", nullable = false)
    private Pessoa pessoa;
    private String obs;

    /** Evento dono do registro; vem sempre da sessao (nunca do JSON enviado pelo frontend) */
    @JsonIgnore
    @Column(name = "EVENTO_CODIGO", nullable = false, updatable = false)
    private String eventoCodigo;
}
