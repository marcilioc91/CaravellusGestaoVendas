package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
@Entity
@Table(name="VENDA_ITEM")
@Data

public class VendaItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "VENDA_ID", nullable = false)
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "PRODUTO_ID", nullable = false)
    private Produto produto;

    private Integer quantidade;

    private BigDecimal precoUnitario;
}
