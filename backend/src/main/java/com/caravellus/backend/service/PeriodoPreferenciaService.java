package com.caravellus.backend.service;

import com.caravellus.backend.util.ArquivoIni;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
        Map<String, String> s = ArquivoIni.ler(arquivo).get(secao);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("inicio", s != null ? s.getOrDefault("inicio", "") : "");
        valores.put("fim", s != null ? s.getOrDefault("fim", "") : "");
        return valores;
    }

    public synchronized void salvar(String secao, LocalDate inicio, LocalDate fim) {
        Map<String, Map<String, String>> ini = ArquivoIni.ler(arquivo);
        Map<String, String> s = new LinkedHashMap<>();
        s.put("inicio", inicio.toString());
        s.put("fim", fim.toString());
        ini.put(secao, s);
        ArquivoIni.gravar(arquivo, ini);
    }
}
