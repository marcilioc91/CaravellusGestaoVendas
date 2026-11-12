package com.caravellus.backend.repository;

import com.caravellus.backend.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByEventoCodigoOrderByNomeAsc(String eventoCodigo);
    Optional<Categoria> findByIdAndEventoCodigo(Long id, String eventoCodigo);
    boolean existsByEventoCodigoAndNomeIgnoreCaseAndIdNot(String eventoCodigo, String nome, Long id);
    boolean existsByEventoCodigoAndNomeIgnoreCase(String eventoCodigo, String nome);

    long countByEventoCodigo(String eventoCodigo);

    @Modifying
    @Query("DELETE FROM Categoria c WHERE c.eventoCodigo = :evento")
    int excluirDoEvento(@Param("evento") String evento);
}
