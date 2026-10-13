package com.caravellus.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class VendaRequestDTO {
    private Long clienteId;
    private Long usuarioId;
    private String usuarioNome;
    private List<VendaProdutoRequestDTO> produtos;
    private String formaPagamento;
    private BigDecimal valorPago;
}
