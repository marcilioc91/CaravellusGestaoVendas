package com.caravellus.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Quem registra a venda vem da sessão (token), não deste corpo. */
@Data
public class VendaRequestDTO {
    private Long clienteId;
    private List<VendaProdutoRequestDTO> produtos;
    private String formaPagamento;
    private BigDecimal valorPago;
}
