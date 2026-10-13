package com.caravellus.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Casos reais do banco migrado (versão 1.0.1) */
class DuplicidadeServiceTest {

    @Test
    void mesmoNomeComAcentoEMaiusculasDiferentes() {
        assertTrue(DuplicidadeService.mesmaPessoa("MARCILIO CABRAL", "81998318034", "Marcílio  Cabral ", null));
    }

    @Test
    void mesmoCelularEMesmoPrimeiroNome() {
        assertTrue(DuplicidadeService.mesmaPessoa("Laís Santos de Souza", "81986437610", "LAIS SOUZA", "81986437610"));
    }

    @Test
    void celularSemONoveOuSemDddContaComoMesmoNumero() {
        assertTrue(DuplicidadeService.mesmaPessoa("Maria Eduarda Fontes", "8194162697", "MARIA EDUARDA C. FONTES", "81994162697"));
        assertTrue(DuplicidadeService.mesmaPessoa("Isabella Inácio", "91917579", "ISABELLA DUCENA", "(91) 99191-7579"));
    }

    @Test
    void mesmoCelularComPrimeiroNomeDiferenteNaoEDuplicado() {
        // Irmãos e casais que compartilham o número
        assertFalse(DuplicidadeService.mesmaPessoa("Guilherme Hytalo de Albuquerque Rijo", "81999721685",
                "ANA GABRIELA DE ALBUQUERQUE RIJO", "81999721685"));
        assertFalse(DuplicidadeService.mesmaPessoa("Thiago Jonathan Almeida do Nascimento", "81995377040",
                "Livia Mirelly Ferreira de Lima Almeida", "81995377040"));
    }

    @Test
    void mesmoPrimeiroNomeSemCelularNaoEDuplicado() {
        assertFalse(DuplicidadeService.mesmaPessoa("MARIA SILVA", null, "MARIA SOUZA", null));
        assertFalse(DuplicidadeService.mesmaPessoa("MARIA SILVA", "", "MARIA SOUZA", "81999990000"));
    }

    @Test
    void nomeVazioNuncaEDuplicado() {
        assertFalse(DuplicidadeService.mesmaPessoa("", "81999990000", "", "81999990000"));
    }
}
