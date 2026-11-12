package com.caravellus.backend.dto;

/** Quantidade de registros de um evento: mostrada antes de excluí-lo e devolvida depois da exclusão */
public record ResumoEventoDTO(String codigo, String nome, long clientes, long categorias, long produtos, long vendas) {
}
