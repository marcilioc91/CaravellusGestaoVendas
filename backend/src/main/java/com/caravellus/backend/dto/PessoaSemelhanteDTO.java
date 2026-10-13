package com.caravellus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Pessoa já cadastrada que parece ser a mesma que está sendo cadastrada. */
@Data
@AllArgsConstructor
public class PessoaSemelhanteDTO {
    private int pessoaId;
    private String nome;
    private String telefone;
    private boolean possuiUsuario;
}
