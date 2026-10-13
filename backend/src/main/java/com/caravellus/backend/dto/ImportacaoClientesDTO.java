package com.caravellus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Resultado da importação de clientes (prévia ou efetivada). */
@Data
public class ImportacaoClientesDTO {

    public enum Situacao { NOVO, DUPLICADO, ERRO }

    /** false: só prévia, nada foi gravado */
    private boolean confirmado;
    /** Na prévia, quantos serão incluídos */
    private int incluidos;
    private int ignorados;
    private int erros;
    private List<Linha> linhas = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Linha {
        /** Linha da planilha (o cabeçalho é a linha 1) */
        private int linha;
        private String nome;
        private String telefone;
        private String obs;
        private Situacao situacao;
        private String motivo;
        private List<String> avisos;
    }
}
