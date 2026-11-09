package com.caravellus.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Atualização da 1.3.3: dados de C:/PortoCabral/dados copiados para C:/Caravellus/dados na primeira execução. */
class PastaDadosTest {

    @TempDir
    Path raiz;

    @Test
    void copiaBancoComNomeNovoEDemaisArquivosSemMexerNaPastaAntiga() throws Exception {
        Path antiga = pastaAntigaComDados();
        Path nova = raiz.resolve("Caravellus/dados");

        PastaDados.migrarPastaAntiga(antiga, nova);

        assertEquals("banco", Files.readString(nova.resolve(PastaDados.BANCO_NOVO)));
        assertEquals("licenca", Files.readString(nova.resolve("licenca.lic")));
        assertEquals("config", Files.readString(nova.resolve("config.properties")));
        assertFalse(Files.exists(nova.resolve(PastaDados.BANCO_ANTIGO)), "o banco entra só com o nome novo");
        assertFalse(Files.exists(nova.resolve(PastaDados.BANCO_NOVO + ".copiando")), "sem temporário sobrando");

        assertTrue(Files.exists(antiga.resolve(PastaDados.BANCO_ANTIGO)), "a pasta antiga fica como backup");
        assertTrue(Files.exists(antiga.resolve("licenca.lic")));
    }

    @Test
    void naoCopiaDeNovoSeOBancoNovoJaExiste() throws Exception {
        Path antiga = pastaAntigaComDados();
        Path nova = Files.createDirectories(raiz.resolve("Caravellus/dados"));
        Files.writeString(nova.resolve(PastaDados.BANCO_NOVO), "vendas feitas na versao nova");

        PastaDados.migrarPastaAntiga(antiga, nova);

        assertEquals("vendas feitas na versao nova", Files.readString(nova.resolve(PastaDados.BANCO_NOVO)));
        assertFalse(Files.exists(nova.resolve("licenca.lic")));
    }

    @Test
    void instalacaoNovaSemPastaAntigaNaoFazNada() {
        Path nova = raiz.resolve("Caravellus/dados");

        PastaDados.migrarPastaAntiga(raiz.resolve("PortoCabral/dados"), nova);

        assertFalse(Files.exists(nova));
    }

    @Test
    void retomaMigracaoInterrompidaAntesDoBanco() throws Exception {
        Path antiga = pastaAntigaComDados();
        Path nova = Files.createDirectories(raiz.resolve("Caravellus/dados"));
        // Queda no meio da cópia anterior: só a licença tinha chegado, mais um temporário incompleto
        Files.writeString(nova.resolve("licenca.lic"), "licenca");
        Files.writeString(nova.resolve(PastaDados.BANCO_NOVO + ".copiando"), "pela met");

        PastaDados.migrarPastaAntiga(antiga, nova);

        assertEquals("banco", Files.readString(nova.resolve(PastaDados.BANCO_NOVO)));
        assertEquals("config", Files.readString(nova.resolve("config.properties")));
        assertFalse(Files.exists(nova.resolve(PastaDados.BANCO_NOVO + ".copiando")));
    }

    private Path pastaAntigaComDados() throws Exception {
        Path antiga = Files.createDirectories(raiz.resolve("PortoCabral/dados"));
        Files.writeString(antiga.resolve(PastaDados.BANCO_ANTIGO), "banco");
        Files.writeString(antiga.resolve("licenca.lic"), "licenca");
        Files.writeString(antiga.resolve("config.properties"), "config");
        return antiga;
    }
}
