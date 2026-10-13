package com.caravellus.backend.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitor de CSV (RFC 4180): campos entre aspas podem ter separador, aspas ("") e quebras de linha.
 * Aceita UTF-8 (com ou sem BOM, como o Google Forms exporta) e Windows-1252 (CSV salvo pelo Excel);
 * o separador (; ou ,) é detectado no cabeçalho.
 */
public final class LeitorCsv {

    private LeitorCsv() {
    }

    /** Registros do arquivo (o primeiro é o cabeçalho); linhas totalmente vazias são descartadas. */
    public static List<List<String>> ler(byte[] conteudo) {
        String texto = decodificar(conteudo);
        return separarRegistros(texto, detectarSeparador(texto));
    }

    private static String decodificar(byte[] conteudo) {
        int inicio = conteudo.length >= 3 && (conteudo[0] & 0xFF) == 0xEF && (conteudo[1] & 0xFF) == 0xBB
                && (conteudo[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer bytes = ByteBuffer.wrap(conteudo, inicio, conteudo.length - inicio);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes)
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(conteudo, inicio, conteudo.length - inicio, Charset.forName("windows-1252"));
        }
    }

    /** Conta ; e , no primeiro registro (fora das aspas) */
    private static char detectarSeparador(String texto) {
        int pontoVirgula = 0;
        int virgula = 0;
        boolean entreAspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') {
                entreAspas = !entreAspas;
            } else if (!entreAspas) {
                if (c == '\n' || c == '\r') {
                    break;
                }
                if (c == ';') pontoVirgula++;
                if (c == ',') virgula++;
            }
        }
        return pontoVirgula > virgula ? ';' : ',';
    }

    private static List<List<String>> separarRegistros(String texto, char separador) {
        List<List<String>> registros = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == separador) {
                atual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                atual.add(campo.toString());
                campo.setLength(0);
                adicionarSeNaoVazio(registros, atual);
                atual = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        atual.add(campo.toString());
        adicionarSeNaoVazio(registros, atual);
        return registros;
    }

    private static void adicionarSeNaoVazio(List<List<String>> registros, List<String> registro) {
        if (registro.stream().anyMatch(v -> !v.isBlank())) {
            registros.add(registro);
        }
    }
}
