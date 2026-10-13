package com.caravellus.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.caravellus.backend.entity.Venda;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Integer> {

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto")
    List<Venda> findAllComItens();

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto WHERE v.cliente.id = :clienteId")
    List<Venda> findByClienteIdComItens(@Param("clienteId") Long clienteId);

    @Query("SELECT DISTINCT v FROM Venda v LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto "
            + "WHERE v.data_venda >= :inicio AND v.data_venda < :fim")
    List<Venda> findPeriodoComItens(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Venda> findByClienteId(Long clienteId);

    List<Venda> findByUsuarioId(Long usuarioId);
}
