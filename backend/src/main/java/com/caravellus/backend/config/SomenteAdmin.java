package com.caravellus.backend.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Endpoint restrito ao perfil ADMIN: os demais usuários recebem 403. Ver AutenticacaoInterceptor. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SomenteAdmin {
}
