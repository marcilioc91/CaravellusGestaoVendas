package com.caravellus.backend.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura e gravação de arquivos .ini simples (seções e chave=valor) da pasta de dados:
 * <pre>
 * [secao]
 * chave=valor
 * </pre>
 * Linhas começando com ";" são comentários. Arquivo inexistente ou ilegível é lido como vazio.
 */
public final class ArquivoIni {

    private ArquivoIni() {}

    /** Seção -> (chave -> valor), na ordem do arquivo */
    public static Map<String, Map<String, String>> ler(Path arquivo) {
        Map<String, Map<String, String>> ini = new LinkedHashMap<>();
        if (!Files.exists(arquivo)) return ini;
        try {
            Map<String, String> atual = null;
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                linha = linha.trim();
                if (linha.startsWith("[") && linha.endsWith("]")) {
                    atual = ini.computeIfAbsent(linha.substring(1, linha.length() - 1), k -> new LinkedHashMap<>());
                } else if (atual != null && linha.contains("=") && !linha.startsWith(";")) {
                    int i = linha.indexOf('=');
                    atual.put(linha.substring(0, i).trim(), linha.substring(i + 1).trim());
                }
            }
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
        return ini;
    }

    /** Regrava o arquivo inteiro (cria a pasta se preciso). Falha de disco: UncheckedIOException. */
    public static void gravar(Path arquivo, Map<String, Map<String, String>> ini) {
        List<String> linhas = new ArrayList<>();
        ini.forEach((nome, chaves) -> {
            linhas.add("[" + nome + "]");
            chaves.forEach((k, v) -> linhas.add(k + "=" + v));
            linhas.add("");
        });
        try {
            Files.createDirectories(arquivo.getParent());
            Files.write(arquivo, linhas, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
