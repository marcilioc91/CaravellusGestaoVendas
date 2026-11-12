package com.caravellus.backend.repository;

import com.caravellus.backend.entity.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    List<AuditoriaLog> findAllByOrderByDataHoraDesc();

    List<AuditoriaLog> findByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    @Query("SELECT a FROM AuditoriaLog a WHERE a.eventoCodigo = :evento OR a.eventoCodigo IS NULL ORDER BY a.dataHora DESC")
    List<AuditoriaLog> listarDoEvento(@Param("evento") String evento);

    @Query("SELECT a FROM AuditoriaLog a WHERE a.usuarioId = :usuarioId AND (a.eventoCodigo = :evento OR a.eventoCodigo IS NULL) "
            + "ORDER BY a.dataHora DESC")
    List<AuditoriaLog> listarDoEventoPorUsuario(@Param("evento") String evento, @Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM AuditoriaLog a WHERE a.eventoCodigo = :evento")
    int excluirDoEvento(@Param("evento") String evento);
}
