package com.caravellus.backend;

import com.caravellus.backend.config.BancoDadosDiretorio;
import com.caravellus.backend.config.PastaDados;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaravellusApplication {

	public static void main(String[] args) {
		PastaDados.definirPadraoSeAusente(args, CaravellusApplication.class);
		SpringApplication app = new SpringApplication(CaravellusApplication.class);
		app.addListeners(new BancoDadosDiretorio());
		var contexto = app.run(args);
		LoggerFactory.getLogger(CaravellusApplication.class)
				.info("Pasta de dados: {} | Banco de dados: {}",
						contexto.getEnvironment().getProperty(PastaDados.PROPRIEDADE),
						contexto.getEnvironment().getProperty("app.db.path"));
	}

}
