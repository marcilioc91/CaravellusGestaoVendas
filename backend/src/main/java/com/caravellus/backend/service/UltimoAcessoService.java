package com.caravellus.backend.service;

import com.caravellus.backend.util.ArquivoIni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Último usuário e último evento usados no login, em {pasta de dados}/ultimo-acesso.ini.
 * A tela de login já abre com os dois preenchidos:
 * <pre>
 * [login]
 * usuario=juliana
 * evento=0002
 * </pre>
 * Só guarda o login (nunca a senha).
 */
@Service
public class UltimoAcessoService {

    private static final Logger log = LoggerFactory.getLogger(UltimoAcessoService.class);
    private static final String SECAO = "login";

    private final Path arquivo;

    public UltimoAcessoService(@Value("${app.dados.dir}") String pastaDados) {
        this.arquivo = Path.of(pastaDados).resolve("ultimo-acesso.ini");
    }

    /** {usuario, evento}; vazios se ninguém entrou ainda */
    public synchronized Map<String, String> obter() {
        Map<String, String> s = ArquivoIni.ler(arquivo).get(SECAO);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("usuario", s != null ? s.getOrDefault("usuario", "") : "");
        valores.put("evento", s != null ? s.getOrDefault("evento", "") : "");
        return valores;
    }

    /** Chamado depois de um login bem-sucedido. Falha ao gravar não impede o login, só fica no log. */
    public synchronized void registrar(String usuario, String evento) {
        try {
            Map<String, Map<String, String>> ini = ArquivoIni.ler(arquivo);
            Map<String, String> s = new LinkedHashMap<>();
            s.put("usuario", usuario);
            s.put("evento", evento);
            ini.put(SECAO, s);
            ArquivoIni.gravar(arquivo, ini);
        } catch (UncheckedIOException e) {
            log.warn("Não foi possível gravar {}: {}", arquivo, e.getMessage());
        }
    }
}
