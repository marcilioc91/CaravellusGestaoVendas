package com.caravellus.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByEventoCodigo(String eventoCodigo);
    Optional<Cliente> findByIdAndEventoCodigo(Long id, String eventoCodigo);
    Optional<Cliente> findByPessoaAndEventoCodigo(Pessoa pessoa, String eventoCodigo);
    /** A pessoa ainda é cliente em algum evento */
    boolean existsByPessoa(Pessoa pessoa);

    long countByEventoCodigo(String eventoCodigo);

    /** clearAutomatically: os Cliente já carregados na memória não podem continuar apontando para pessoas apagadas */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Cliente c WHERE c.eventoCodigo = :evento")
    int excluirDoEvento(@Param("evento") String evento);
}
