package com.caravellus.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda o ultimo periodo (inicio/fim) informado em cada tela em {pasta de dados}/periodos.ini:
 * <pre>
 * [relatorio]
 * inicio=2026-10-01
 * fim=2026-10-31
 * </pre>
 */
@Service
public class PeriodoPreferenciaService {

    private final Path arquivo;

    public PeriodoPreferenciaService(@Value("${app.dados.dir}") String pastaDados) {
        this.arquivo = Path.of(pastaDados).resolve("periodos.ini");
    }

    public synchronized Map<String, String> obter(String secao) {
        Map<String, String> s = ler().get(secao);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("inicio", s != null ? s.getOrDefault("inicio", "") : "");
        valores.put("fim", s != null ? s.getOrDefault("fim", "") : "");
        return valores;
    }

    public synchronized void salvar(String secao, LocalDate inicio, LocalDate fim) {
        Map<String, Map<String, String>> ini = ler();
        Map<String, String> s = new LinkedHashMap<>();
        s.put("inicio", inicio.toString());
        s.put("fim", fim.toString());
        ini.put(secao, s);

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

    private Map<String, Map<String, String>> ler() {
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
}
