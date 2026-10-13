package com.caravellus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Dados de um cliente já cadastrado, usados para preencher o cadastro de usuário (promoção a usuário). */
@Data
@AllArgsConstructor
public class ClienteExistenteDTO {
    private String nome;
    private String telefone;
}
