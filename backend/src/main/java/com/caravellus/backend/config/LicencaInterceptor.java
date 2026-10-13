package com.caravellus.backend.config;

import com.caravellus.backend.controller.LicencaController;
import com.caravellus.backend.service.LicencaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bloqueia a API enquanto o sistema não estiver ativado: qualquer @RestController responde
 * 423 (Locked), exceto LicencaController. O frontend (arquivos estáticos e SpaController)
 * continua acessível para exibir a tela de ativação.
 */
@Component
public class LicencaInterceptor implements HandlerInterceptor {

    public static final int STATUS_BLOQUEADO = 423;

    @Autowired
    private LicencaService licencaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        Class<?> controller = metodo.getBeanType();
        if (controller == LicencaController.class
                || !AnnotatedElementUtils.hasAnnotation(controller, RestController.class)) {
            return true;
        }
        if (licencaService.isAtiva()) return true;

        response.setStatus(STATUS_BLOQUEADO);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Sistema não ativado. Informe a chave de licença para continuar.");
        return false;
    }
}
