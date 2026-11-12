package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Evento (acampamento, conferencia...): separa clientes, categorias, produtos, vendas e auditoria.
 * Escolhido na tela de login. O codigo (ate 4 caracteres) e a chave e nao muda depois de criado.
 */
@Entity
@Table(name = "EVENTO")
@Data
public class Evento {
    @Id
    @Column(length = 4)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    /** Inativo: some da tela de login, mas os dados continuam guardados */
    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "DATA_CRIACAO", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
