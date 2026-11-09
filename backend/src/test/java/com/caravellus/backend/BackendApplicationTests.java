package com.caravellus.backend;

import org.junit.jupiter.api.Test;

/** O contexto sobe por completo: Flyway aplica as migrations e o Hibernate valida as entidades contra o banco. */
class BackendApplicationTests extends IntegracaoTest {

	@Test
	void contextLoads() {
	}

}
