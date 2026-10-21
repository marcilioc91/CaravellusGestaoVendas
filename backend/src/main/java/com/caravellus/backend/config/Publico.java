package com.caravellus.backend.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Endpoint acessível sem login (ex.: o próprio login e a recuperação de senha). Ver AutenticacaoInterceptor. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Publico {
}
