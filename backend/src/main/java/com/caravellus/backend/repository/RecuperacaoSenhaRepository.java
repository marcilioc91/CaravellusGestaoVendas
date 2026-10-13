package com.caravellus.backend.repository;

import com.caravellus.backend.entity.RecuperacaoSenha;
import com.caravellus.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {
    List<RecuperacaoSenha> findByUsuarioAndUsadoFalse(Usuario usuario);
    Optional<RecuperacaoSenha> findFirstByUsuarioAndUsadoFalseOrderByCriadoEmDesc(Usuario usuario);
}
