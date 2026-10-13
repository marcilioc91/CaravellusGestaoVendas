package com.caravellus.backend.repository;

import com.caravellus.backend.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
    boolean existsByNomeIgnoreCase(String nome);
}
