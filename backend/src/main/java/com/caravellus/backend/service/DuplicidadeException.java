package com.caravellus.backend.service;

import com.caravellus.backend.dto.PessoaSemelhanteDTO;

import java.util.List;
import java.util.Map;

/** Cadastro de uma pessoa que provavelmente já existe; a tela pede confirmação antes de gravar. */
public class DuplicidadeException extends RuntimeException {

    private final List<PessoaSemelhanteDTO> semelhantes;

    public DuplicidadeException(List<PessoaSemelhanteDTO> semelhantes) {
        super("Já existe cadastro parecido com este.");
        this.semelhantes = semelhantes;
    }

    /** Corpo da resposta 409 */
    public Map<String, Object> resposta() {
        return Map.of("mensagem", getMessage(), "semelhantes", semelhantes);
    }
}
