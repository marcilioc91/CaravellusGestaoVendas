package com.caravellus.backend.dto;

import lombok.Data;

@Data
public class CadastroRequestDTO {
    private String nome;
    private String cpf;
    private String email;
    private String telefone;
    private String usuario;
    private String senha;
    private String obs;
    /** true: grava mesmo havendo cadastro parecido (o usuário confirmou que é outra pessoa) */
    private boolean confirmarDuplicidade;
    /** "Sou eu": cria o acesso para este cadastro existente, apontado pelo aviso de duplicidade */
    private Integer pessoaIdExistente;
}
