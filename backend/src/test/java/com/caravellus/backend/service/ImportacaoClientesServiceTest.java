package com.caravellus.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Formatos de celular vindos do Google Forms */
class ImportacaoClientesServiceTest {

    @Test
    void normalizaCelularDigitadoDeVariasFormas() {
        assertEquals("81982943302", ImportacaoClientesService.normalizarTelefone("81 98294-3302"));
        assertEquals("81986170329", ImportacaoClientesService.normalizarTelefone("(81) 98617-0329"));
        assertEquals("81987195975", ImportacaoClientesService.normalizarTelefone("81.9.8719-5975 "));
        assertEquals("81998252592", ImportacaoClientesService.normalizarTelefone("558198252592"));
    }

    @Test
    void poeONoveEmCelularAntigoEMantemFixoESemDdd() {
        assertEquals("81988455083", ImportacaoClientesService.normalizarTelefone("81 8845-5083"));
        assertEquals("8134561234", ImportacaoClientesService.normalizarTelefone("(81) 3456-1234"));
        assertEquals("91917579", ImportacaoClientesService.normalizarTelefone("91917579"));
        assertNull(ImportacaoClientesService.normalizarTelefone(" "));
    }

    @Test
    void nomeEmMaiusculasComEspacosSimples() {
        assertEquals("BIANCA NIELLE DE SOUZA", ImportacaoClientesService.formatarNome(" Bianca  Nielle de Souza "));
        assertEquals("JÚLIA ÁVILA", ImportacaoClientesService.formatarNome("Júlia Ávila"));
    }
}
