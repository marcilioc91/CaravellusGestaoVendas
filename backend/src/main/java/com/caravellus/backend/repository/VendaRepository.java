package com.caravellus.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.caravellus.backend.entity.Venda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Integer> {

    /** Tudo o que o historico serializa, em um unico SELECT (evita N+1) */
    String SELECT_COMPLETO = "SELECT DISTINCT v FROM Venda v "
            + "LEFT JOIN FETCH v.cliente c LEFT JOIN FETCH c.pessoa "
            + "LEFT JOIN FETCH v.usuario u LEFT JOIN FETCH u.pessoa "
            + "LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto p LEFT JOIN FETCH p.categoria ";

    @Query(SELECT_COMPLETO)
    List<Venda> findAllComItens();

    @Query(SELECT_COMPLETO + "WHERE c.id = :clienteId")
    List<Venda> findByClienteIdComItens(@Param("clienteId") Long clienteId);

    @Query(SELECT_COMPLETO + "WHERE v.data_venda >= :inicio AND v.data_venda < :fim")
    List<Venda> findPeriodoComItens(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Venda> findByClienteId(Long clienteId);

    List<Venda> findByUsuarioId(Long usuarioId);

    @Modifying
    @Query(
            "UPDATE VendaItem vi SET vi.precoUnitario = :preco " +
            "WHERE vi.produto.id = :produtoId AND vi.venda.formaPagamento = 'PENDENTE'"
    )
    int atualizarPrecoItens(@Param("produtoId") Long produtoId, @Param("preco") BigDecimal preco);
}
