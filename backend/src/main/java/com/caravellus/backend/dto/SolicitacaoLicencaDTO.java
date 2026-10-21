package com.caravellus.backend.dto;

import lombok.Data;

/** Pedido de licença feito na tela de ativação */
@Data
public class SolicitacaoLicencaDTO {
    private String nome;
    private String email;
    private String telefone;
}
