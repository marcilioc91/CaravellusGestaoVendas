package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "PRODUTO_IMAGEM")
@Data

public class ProdutoImagem {
    @Id
    @Column(name = "PRODUTO_ID")
    private Long produtoId;

    private String tipo;

    @Lob
    private byte[] conteudo;
}
