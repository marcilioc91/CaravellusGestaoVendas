package com.caravellus.backend.dto;

import com.caravellus.backend.entity.Usuario;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resposta do login: os campos do usuário mais o token da sessão, que o frontend envia
 * em "Authorization: Bearer <token>". token null: o usuário ainda precisa trocar a senha.
 */
@Getter
@AllArgsConstructor
public class SessaoDTO {

    @JsonUnwrapped
    private Usuario usuario;

    private String token;
}
