package com.caravellus.backend.repository;

import com.caravellus.backend.entity.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, String> {
    List<Evento> findAllByOrderByNomeAsc();
    List<Evento> findByAtivoTrueOrderByNomeAsc();
    boolean existsByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCaseAndCodigoNot(String nome, String codigo);
    long countByAtivoTrue();
}
