package com.caravellus.backend.config;

import org.springframework.boot.system.ApplicationHome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Define a pasta de dados (banco .fdb e config.properties) quando ela não é informada:
 * - desenvolvimento (rodando de dentro de backend/target): backend/dados, junto do projeto;
 * - sistema instalado: C:/Caravellus/dados, fora da pasta do programa
 *   (sobrevive a atualizações e desinstalação).
 * Pode ser trocada com --app.dados.dir=..., -Dapp.dados.dir=... ou a variável APP_DADOS_DIR.
 *
 * Até a versão 1.3.3 a pasta era C:/PortoCabral/dados e o banco PORTOCABRAL.FDB: na primeira
 * execução da versão nova, os dados são copiados de lá (migrarPastaAntiga).
 */
public final class PastaDados {

    public static final String PROPRIEDADE = "app.dados.dir";
    static final Path PADRAO_INSTALADO = Path.of("C:/Caravellus/dados");

    static final Path PASTA_ANTIGA = Path.of("C:/PortoCabral/dados");
    static final String BANCO_ANTIGO = "PORTOCABRAL.FDB";
    /** Mesmo nome usado em app.db.path (application.properties) */
    static final String BANCO_NOVO = "CARAVELLUS.FDB";

    private PastaDados() {}

    public static void definirPadraoSeAusente(String[] args, Class<?> classePrincipal) {
        boolean informada = System.getProperty(PROPRIEDADE) != null
                || System.getenv("APP_DADOS_DIR") != null
                || Arrays.stream(args).anyMatch(a -> a.startsWith("--" + PROPRIEDADE + "="));
        if (informada) return;

        Path pasta = padrao(classePrincipal);
        if (pasta.equals(PADRAO_INSTALADO)) migrarPastaAntiga(PASTA_ANTIGA, pasta);
        System.setProperty(PROPRIEDADE, pasta.toString().replace(File.separatorChar, '/'));
    }

    static Path padrao(Class<?> classePrincipal) {
        File origem = new ApplicationHome(classePrincipal).getSource();
        if (origem != null) {
            // Sobe a partir de target/classes (IDE) ou target/app.jar (mvn package) até achar o módulo Maven
            for (Path p = origem.toPath().toAbsolutePath(); p != null; p = p.getParent()) {
                Path pai = p.getParent();
                if (p.getFileName() != null && p.getFileName().toString().equals("target")
                        && pai != null && Files.exists(pai.resolve("pom.xml"))) {
                    return pai.resolve("dados");
                }
            }
        }
        return PADRAO_INSTALADO;
    }

    /**
     * Copia para a pasta nova tudo o que estava na antiga (banco, licenca.lic, config.properties...),
     * uma única vez: só quando a nova ainda não tem banco e a antiga tem.
     * A pasta antiga não é alterada e fica como cópia de segurança.
     */
    static void migrarPastaAntiga(Path antiga, Path nova) {
        Path bancoAntigo = antiga.resolve(BANCO_ANTIGO);
        Path bancoNovo = nova.resolve(BANCO_NOVO);
        if (Files.exists(bancoNovo) || !Files.exists(bancoAntigo)) return;

        try {
            Files.createDirectories(nova);

            // 1. Demais arquivos; REPLACE_EXISTING permite repetir a migracao se ela parou no meio
            List<Path> arquivos;
            try (Stream<Path> lista = Files.list(antiga)) {
                arquivos = lista.filter(Files::isRegularFile).filter(p -> !p.equals(bancoAntigo)).toList();
            }
            for (Path origem : arquivos) {
                Files.copy(origem, nova.resolve(origem.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Banco por ultimo, via temporario: CARAVELLUS.FDB so passa a existir com a copia completa
            Path temporario = nova.resolve(BANCO_NOVO + ".copiando");
            Files.copy(bancoAntigo, temporario, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporario, bancoNovo, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível copiar os dados de " + antiga + " para " + nova + ".", e);
        }
    }
}
