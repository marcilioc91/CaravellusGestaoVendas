package com.caravellus.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelatorioInventarioItemDTO {
    private String nomeProduto;
    private String nomeCategoria;
    private Integer quantidadeVendida;
    private BigDecimal totalReceita;
    private BigDecimal totalCusto;
    private BigDecimal lucro;

    public RelatorioInventarioItemDTO(String nomeProduto, String nomeCategoria) {
        this.nomeProduto = nomeProduto;
        this.nomeCategoria = nomeCategoria;
        this.quantidadeVendida = 0;
        this.totalReceita = BigDecimal.ZERO;
        this.totalCusto = BigDecimal.ZERO;
        this.lucro = BigDecimal.ZERO;
    }
}
