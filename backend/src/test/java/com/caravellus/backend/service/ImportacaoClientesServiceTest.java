package com.caravellus.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Formatos de celular vindos do Google Forms */
class ImportacaoClientesServiceTest {

    @Test
    void normalizaCelularDigitadoDeVariasFormas() {
        assertEquals("81990001001", ImportacaoClientesService.normalizarTelefone("81 99000-1001"));
        assertEquals("81990001002", ImportacaoClientesService.normalizarTelefone("(81) 99000-1002"));
        assertEquals("81990001003", ImportacaoClientesService.normalizarTelefone("81.9.9000-1003 "));
        assertEquals("81999001004", ImportacaoClientesService.normalizarTelefone("558199001004"));
    }

    @Test
    void poeONoveEmCelularAntigoEMantemFixoESemDdd() {
        assertEquals("81988001005", ImportacaoClientesService.normalizarTelefone("81 8800-1005"));
        assertEquals("8134561234", ImportacaoClientesService.normalizarTelefone("(81) 3456-1234"));
        assertEquals("90001006", ImportacaoClientesService.normalizarTelefone("90001006"));
        assertNull(ImportacaoClientesService.normalizarTelefone(" "));
    }

    @Test
    void nomeEmMaiusculasComEspacosSimples() {
        assertEquals("BIANCA NIELLE DE SOUZA", ImportacaoClientesService.formatarNome(" Bianca  Nielle de Souza "));
        assertEquals("JÚLIA ÁVILA", ImportacaoClientesService.formatarNome("Júlia Ávila"));
    }
}
