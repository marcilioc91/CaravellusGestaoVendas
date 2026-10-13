package com.caravellus.backend.dto;

import lombok.Data;

@Data
public class TrocarSenhaDTO {
    private String login;
    private String senhaAtual;
    private String novaSenha;
}
