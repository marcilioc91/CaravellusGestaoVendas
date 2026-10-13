package com.caravellus.backend.repository;

import com.caravellus.backend.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCategoriaId(Long categoriaId);

    @Modifying
    @Query("UPDATE Produto p SET p.imagemVersao = :versao WHERE p.id = :id")
    void atualizarImagemVersao(@Param("id") Long id, @Param("versao") Long versao);
}
