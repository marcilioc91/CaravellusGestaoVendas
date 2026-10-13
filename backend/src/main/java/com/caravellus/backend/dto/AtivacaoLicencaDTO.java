package com.caravellus.backend.dto;

import lombok.Data;

@Data
public class AtivacaoLicencaDTO {
    /** Chave de licença gerada pelo fornecedor para o código desta máquina */
    private String chave;
}
