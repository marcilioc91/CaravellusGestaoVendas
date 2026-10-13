package com.caravellus.backend.config;

import org.springframework.boot.system.ApplicationHome;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Define a pasta de dados (banco .fdb e config.properties) quando ela não é informada:
 * - desenvolvimento (rodando de dentro de backend/target): backend/dados, junto do projeto;
 * - sistema instalado: C:/PortoCabral/dados, fora da pasta do programa
 *   (sobrevive a atualizações e desinstalação).
 * Pode ser trocada com --app.dados.dir=..., -Dapp.dados.dir=... ou a variável APP_DADOS_DIR.
 */
public final class PastaDados {

    public static final String PROPRIEDADE = "app.dados.dir";
    static final Path PADRAO_INSTALADO = Path.of("C:/PortoCabral/dados");

    private PastaDados() {}

    public static void definirPadraoSeAusente(String[] args, Class<?> classePrincipal) {
        boolean informada = System.getProperty(PROPRIEDADE) != null
                || System.getenv("APP_DADOS_DIR") != null
                || Arrays.stream(args).anyMatch(a -> a.startsWith("--" + PROPRIEDADE + "="));
        if (informada) return;
        System.setProperty(PROPRIEDADE, padrao(classePrincipal).toString().replace(File.separatorChar, '/'));
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
}
