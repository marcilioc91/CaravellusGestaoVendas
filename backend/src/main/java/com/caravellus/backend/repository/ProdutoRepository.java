package com.caravellus.backend.repository;

import com.caravellus.backend.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCategoriaId(Long categoriaId);
    List<Produto> findByEventoCodigoOrderByNomeAsc(String eventoCodigo);
    Optional<Produto> findByIdAndEventoCodigo(Long id, String eventoCodigo);

    long countByEventoCodigo(String eventoCodigo);

    @Modifying
    @Query("DELETE FROM Produto p WHERE p.eventoCodigo = :evento")
    int excluirDoEvento(@Param("evento") String evento);

    @Modifying
    @Query("UPDATE Produto p SET p.imagemVersao = :versao WHERE p.id = :id")
    void atualizarImagemVersao(@Param("id") Long id, @Param("versao") Long versao);
}
