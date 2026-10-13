package com.caravellus.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByPessoa(Pessoa pessoa);
}
