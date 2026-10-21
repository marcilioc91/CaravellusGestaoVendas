package com.caravellus.backend.config;

import com.caravellus.backend.controller.LicencaController;
import com.caravellus.backend.service.SessaoService;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Exige login em toda a API: o frontend envia "Authorization: Bearer <token>" recebido no login.
 * Sem token válido: 401. Endpoint com @SomenteAdmin e usuário que não é ADMIN: 403.
 * Liberados: LicencaController, métodos com @Publico e o frontend (arquivos estáticos e SpaController).
 *
 * O usuário da sessão fica no atributo USUARIO da requisição (@RequestAttribute nos controllers).
 * Perfil e identidade vêm do servidor, nunca de cabeçalhos enviados pelo cliente.
 */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    public static final String USUARIO = "usuarioSessao";
    private static final String PREFIXO = "Bearer ";

    @Autowired
    private SessaoService sessaoService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        Class<?> controller = metodo.getBeanType();
        if (controller == LicencaController.class
                || !AnnotatedElementUtils.hasAnnotation(controller, RestController.class)
                || metodo.hasMethodAnnotation(Publico.class)) {
            return true;
        }

        Optional<UsuarioSessao> usuario = sessaoService.validar(token(request));
        if (usuario.isEmpty()) {
            return negar(response, 401, "Sessão expirada. Entre novamente no sistema.");
        }
        if (metodo.hasMethodAnnotation(SomenteAdmin.class) && !usuario.get().isAdmin()) {
            return negar(response, 403, "Acesso negado.");
        }
        request.setAttribute(USUARIO, usuario.get());
        return true;
    }

    public static String token(HttpServletRequest request) {
        String cabecalho = request.getHeader("Authorization");
        return cabecalho != null && cabecalho.startsWith(PREFIXO) ? cabecalho.substring(PREFIXO.length()).trim() : null;
    }

    private static boolean negar(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(mensagem);
        return false;
    }
}
