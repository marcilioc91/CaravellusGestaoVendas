package com.caravellus.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="VENDA")
@Data
public class Venda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "CLIENTE_ID", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL)
    private List<VendaItem> itens;

    @CreationTimestamp
    private LocalDateTime data_venda;

    private String formaPagamento;
    private BigDecimal valorPago;

    /** Evento dono do registro; vem sempre da sessao (nunca do JSON enviado pelo frontend) */
    @JsonIgnore
    @Column(name = "EVENTO_CODIGO", nullable = false, updatable = false)
    private String eventoCodigo;
}
