package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "PESSOA")
@Data
public class Pessoa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String nome;
    private String cpf;
    private String telefone;
}
