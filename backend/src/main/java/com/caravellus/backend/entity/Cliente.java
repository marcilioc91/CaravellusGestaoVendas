package com.caravellus.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="CLIENTE")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    @JoinColumn(name = "PESSOA_ID", nullable = false, unique = true)
    private Pessoa pessoa;
    private String obs;
}
