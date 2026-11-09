package com.caravellus.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base dos testes de integração: sobe o Spring com o Firebird embarcado de verdade, num banco
 * próprio em target/test-dados (o Flyway cria a estrutura na primeira execução; "mvn clean" apaga).
 *
 * Em produção a pasta de dados é definida no main() (PastaDados); aqui ela vem das propriedades.
 * O envio de e-mail é simulado (JavaMailSender mockado) e as credenciais SMTP são fictícias.
 * Todas as classes usam exatamente a mesma configuração, então o contexto é criado uma vez só.
 *
 * Os testes não são @Transactional: cada serviço confirma ou desfaz a própria transação, como em
 * produção. Por isso cada teste cria os próprios dados (nomes únicos) em vez de depender de limpeza.
 */
@SpringBootTest(properties = {
        "app.dados.dir=${user.dir}/target/test-dados",
        "spring.mail.username=teste",
        "spring.mail.password=teste",
        "app.mail.remetente=teste@caravellus.local"
})
public abstract class IntegracaoTest {

    static {
        try {
            Files.createDirectories(Path.of(System.getProperty("user.dir"), "target", "test-dados"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @MockitoBean
    protected JavaMailSender mailSender;
}
