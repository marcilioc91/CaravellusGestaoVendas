package com.caravellus.backend.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeitorCsvTest {

    @Test
    void csvDoGoogleFormsComVirgulaAspasEQuebraDeLinhaNoCampo() {
        String csv = "Nome completo,\"Está tomando medicamento?\nobs.: levar\",Lote\r\n"
                + "Monalisa Cardoso,Não,\"1º Lote: R$ 400,04\"\r\n";

        List<List<String>> registros = LeitorCsv.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, registros.size());
        assertEquals(List.of("Nome completo", "Está tomando medicamento?\nobs.: levar", "Lote"), registros.get(0));
        assertEquals(List.of("Monalisa Cardoso", "Não", "1º Lote: R$ 400,04"), registros.get(1));
    }

    @Test
    void modeloComPontoEVirgulaEmWindows1252() {
        String csv = "Nome;CPF;Telefone;Observação\r\nJOÃO;;81999990000;\"diz \"\"oi\"\"\"\r\n\r\n";

        List<List<String>> registros = LeitorCsv.ler(csv.getBytes(Charset.forName("windows-1252")));

        assertEquals(2, registros.size());
        assertEquals("Observação", registros.get(0).get(3));
        assertEquals(List.of("JOÃO", "", "81999990000", "diz \"oi\""), registros.get(1));
    }

    @Test
    void ignoraBomDoUtf8() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] texto = "Nome\nANA".getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + texto.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(texto, 0, conteudo, bom.length, texto.length);

        assertEquals("Nome", LeitorCsv.ler(conteudo).get(0).get(0));
    }
}
