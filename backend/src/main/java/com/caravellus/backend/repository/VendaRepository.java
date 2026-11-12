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
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Integer> {

    /** Tudo o que o historico serializa, em um unico SELECT (evita N+1) */
    String SELECT_COMPLETO = "SELECT DISTINCT v FROM Venda v "
            + "LEFT JOIN FETCH v.cliente c LEFT JOIN FETCH c.pessoa "
            + "LEFT JOIN FETCH v.usuario u LEFT JOIN FETCH u.pessoa "
            + "LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto p LEFT JOIN FETCH p.categoria ";

    @Query(SELECT_COMPLETO + "WHERE v.eventoCodigo = :evento")
    List<Venda> findAllComItens(@Param("evento") String evento);

    @Query(SELECT_COMPLETO + "WHERE v.eventoCodigo = :evento AND c.id = :clienteId")
    List<Venda> findByClienteIdComItens(@Param("evento") String evento, @Param("clienteId") Long clienteId);

    @Query(SELECT_COMPLETO + "WHERE v.eventoCodigo = :evento AND v.data_venda >= :inicio AND v.data_venda < :fim")
    List<Venda> findPeriodoComItens(@Param("evento") String evento,
                                    @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    Optional<Venda> findByIdAndEventoCodigo(Long id, String eventoCodigo);

    long countByEventoCodigo(String eventoCodigo);

    /** Exclusão do evento: itens primeiro (chave estrangeira para VENDA) */
    @Modifying
    @Query("DELETE FROM VendaItem vi WHERE vi.venda.id IN (SELECT v.id FROM Venda v WHERE v.eventoCodigo = :evento)")
    int excluirItensDoEvento(@Param("evento") String evento);

    @Modifying
    @Query("DELETE FROM Venda v WHERE v.eventoCodigo = :evento")
    int excluirDoEvento(@Param("evento") String evento);

    List<Venda> findByClienteId(Long clienteId);

    @Modifying
    @Query(
            "UPDATE VendaItem vi SET vi.precoUnitario = :preco " +
            "WHERE vi.produto.id = :produtoId AND vi.venda.formaPagamento = 'PENDENTE'"
    )
    int atualizarPrecoItens(@Param("produtoId") Long produtoId, @Param("preco") BigDecimal preco);
}
