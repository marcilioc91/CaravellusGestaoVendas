package com.caravellus.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Padrões encontrados no banco migrado (versão 1.0.1), reproduzidos com nomes e números fictícios */
class DuplicidadeServiceTest {

    @Test
    void mesmoNomeComAcentoEMaiusculasDiferentes() {
        assertTrue(DuplicidadeService.mesmaPessoa("JOSÉ ALMEIDA", "81990000001", "José  Almeida ", null));
    }

    @Test
    void mesmoCelularEMesmoPrimeiroNome() {
        assertTrue(DuplicidadeService.mesmaPessoa("Laís Ferreira de Souza", "81990000002", "LAIS SOUZA", "81990000002"));
    }

    @Test
    void celularSemONoveOuSemDddContaComoMesmoNumero() {
        assertTrue(DuplicidadeService.mesmaPessoa("Ana Beatriz Lima", "8190000003", "ANA BEATRIZ C. LIMA", "81990000003"));
        assertTrue(DuplicidadeService.mesmaPessoa("Júlia Inácio", "90000004", "JULIA MOURA", "(81) 99000-0004"));
    }

    @Test
    void mesmoCelularComPrimeiroNomeDiferenteNaoEDuplicado() {
        // Irmãos e casais que compartilham o número
        assertFalse(DuplicidadeService.mesmaPessoa("Pedro Henrique de Albuquerque Rocha", "81990000005",
                "ANA CLARA DE ALBUQUERQUE ROCHA", "81990000005"));
        assertFalse(DuplicidadeService.mesmaPessoa("Carlos Eduardo Almeida do Nascimento", "81990000006",
                "Fernanda Ferreira de Lima Almeida", "81990000006"));
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
