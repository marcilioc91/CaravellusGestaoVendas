package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "USUARIO")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "PESSOA_ID", nullable = false, unique = true)
    private Pessoa pessoa;

    @Column(name = "USUARIO_LOGIN", unique = true, nullable = false)
    private String usuarioLogin;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERFIL", nullable = false)
    private PerfilUsuario perfil = PerfilUsuario.OPERADOR;

    @CreationTimestamp
    private LocalDateTime data_criacao;

    /** Quando true, o usuário precisa definir uma nova senha antes de usar o sistema */
    @Column(name = "TROCAR_SENHA", nullable = false)
    private Boolean trocarSenha = false;
}
