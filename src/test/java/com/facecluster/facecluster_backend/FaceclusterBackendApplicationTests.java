package com.facecluster.facecluster_backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de integración: arranca todo el contexto y necesita MongoDB y las
 * variables AWS_* reales. Se deshabilita en las pruebas unitarias (PR-01,
 * "sin llamadas reales a BD"). Corresponde a PR-02 (Testcontainers).
 */
@Disabled("Requiere MongoDB y AWS reales; pasa a PR-02 (integración)")
@SpringBootTest
class FaceclusterBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
