package com.facecluster.facecluster_backend.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** PR-01 – El filtro CORS solo permite el origen del frontend Angular local. */
class CorsConfigTest {

    private final CorsFilter filter = new CorsConfig().corsFilter();

    private MockHttpServletResponse preflight(String origin, String method)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/reports");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("Permite el origen http://localhost:4200")
    void allowsAngularOrigin() throws Exception {
        MockHttpServletResponse response = preflight("http://localhost:4200", "PUT");

        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:4200", response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PUT"));
    }

    @Test
    @DisplayName("Rechaza orígenes no configurados")
    void rejectsOtherOrigins() throws Exception {
        MockHttpServletResponse response = preflight("http://sitio-malicioso.com", "GET");

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Rechaza métodos no permitidos (PATCH)")
    void rejectsDisallowedMethods() throws Exception {
        MockHttpServletResponse response = preflight("http://localhost:4200", "PATCH");

        assertEquals(403, response.getStatus());
    }
}
