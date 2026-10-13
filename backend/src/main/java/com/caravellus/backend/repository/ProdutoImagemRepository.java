package com.caravellus.backend.repository;

import com.caravellus.backend.entity.ProdutoImagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoImagemRepository extends JpaRepository<ProdutoImagem, Long> {
}
