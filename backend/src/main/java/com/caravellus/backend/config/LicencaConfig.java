package com.caravellus.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LicencaConfig implements WebMvcConfigurer {

    @Autowired
    private LicencaInterceptor licencaInterceptor;

    @Autowired
    private AutenticacaoInterceptor autenticacaoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Licença primeiro: sistema não ativado responde 423 antes de exigir login
        registry.addInterceptor(licencaInterceptor);
        registry.addInterceptor(autenticacaoInterceptor);
    }
}
