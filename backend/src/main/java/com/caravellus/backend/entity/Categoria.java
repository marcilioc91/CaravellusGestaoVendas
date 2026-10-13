package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="CATEGORIA")
@Data

public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
}
