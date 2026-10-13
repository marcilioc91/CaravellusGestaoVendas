package com.caravellus.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PagamentoRequestDTO {
    private String formaPagamento;
    private BigDecimal valorPago;
}
