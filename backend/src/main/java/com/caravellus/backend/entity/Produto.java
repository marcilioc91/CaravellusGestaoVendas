package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name="PRODUTO")
@Data

public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private BigDecimal preco;
    private BigDecimal preco_custo;
    private Integer estoque;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Só muda pelos endpoints de imagem; null = produto sem imagem */
    @Column(insertable = false, updatable = false)
    private Long imagemVersao;

    /** Evento dono do registro; vem sempre da sessao (nunca do JSON enviado pelo frontend) */
    @JsonIgnore
    @Column(name = "EVENTO_CODIGO", nullable = false, updatable = false)
    private String eventoCodigo;
}
