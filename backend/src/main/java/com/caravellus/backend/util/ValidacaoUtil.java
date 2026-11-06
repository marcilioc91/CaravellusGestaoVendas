package com.caravellus.backend.util;

import java.util.regex.Pattern;

public class ValidacaoUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w+.%-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    private ValidacaoUtil() {}

    public static boolean cpfValido(String cpf) {
        if (cpf == null) return false;
        String numeros = cpf.replaceAll("[^0-9]", "");
        if (numeros.length() != 11) return false;
        if (numeros.chars().distinct().count() == 1) return false;

        int soma = 0;
        for (int i = 0; i < 9; i++) soma += (numeros.charAt(i) - '0') * (10 - i);
        int d1 = 11 - (soma % 11);
        if (d1 >= 10) d1 = 0;
        if (d1 != (numeros.charAt(9) - '0')) return false;

        soma = 0;
        for (int i = 0; i < 10; i++) soma += (numeros.charAt(i) - '0') * (11 - i);
        int d2 = 11 - (soma % 11);
        if (d2 >= 10) d2 = 0;
        return d2 == (numeros.charAt(10) - '0');
    }

    public static boolean emailValido(String email) {
        if (email == null || email.isBlank()) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /** Tamanho mínimo de toda senha nova (cadastro, troca, recuperação e reset pelo admin); o front usa o mesmo valor */
    public static final int SENHA_TAMANHO_MINIMO = 6;

    /** Lança IllegalArgumentException se a senha estiver vazia ou tiver menos que SENHA_TAMANHO_MINIMO caracteres */
    public static void validarSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new IllegalArgumentException("A senha não pode ser vazia.");
        }
        if (senha.length() < SENHA_TAMANHO_MINIMO) {
            throw new IllegalArgumentException("A senha deve ter no mínimo " + SENHA_TAMANHO_MINIMO + " caracteres.");
        }
    }
}
