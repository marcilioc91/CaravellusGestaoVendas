package com.caravellus.backend.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Garante que a pasta do arquivo .fdb exista antes da conexão com o banco:
 * o Firebird cria o arquivo do banco, mas não cria diretórios.
 */
public class BancoDadosDiretorio implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String caminho = event.getEnvironment().getProperty("app.db.path");
        if (caminho == null || caminho.isBlank()) return;

        Path pasta = Path.of(caminho).toAbsolutePath().getParent();
        if (pasta == null) return;
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta do banco de dados: " + pasta, e);
        }
    }
}
