package com.caravellus.backend.dto;

import lombok.Data;

@Data
public class RecuperacaoSenhaDTO {
    /** Login ou e-mail do usuário */
    private String identificador;
    private String codigo;
    private String novaSenha;
}
