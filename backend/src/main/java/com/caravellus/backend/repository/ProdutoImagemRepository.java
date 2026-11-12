package com.caravellus.backend.repository;

import com.caravellus.backend.entity.ProdutoImagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoImagemRepository extends JpaRepository<ProdutoImagem, Long> {

    /** Exclusão do evento: imagens antes dos produtos (chave estrangeira para PRODUTO) */
    @Modifying
    @Query("DELETE FROM ProdutoImagem i WHERE i.produtoId IN (SELECT p.id FROM Produto p WHERE p.eventoCodigo = :evento)")
    int excluirDoEvento(@Param("evento") String evento);
}
