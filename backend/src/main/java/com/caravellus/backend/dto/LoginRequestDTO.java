package com.caravellus.backend.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String login;
    private String senha;
    /** Código do evento escolhido na tela de login */
    private String evento;
}
