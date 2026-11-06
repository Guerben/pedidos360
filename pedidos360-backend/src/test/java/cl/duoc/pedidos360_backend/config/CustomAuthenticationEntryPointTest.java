package cl.duoc.pedidos360_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class CustomAuthenticationEntryPointTest {

    private CustomAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new CustomAuthenticationEntryPoint(objectMapper);
    }

    @Test
    void commence_retornaHttp401YJsonEstructurado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/pedidos");
        request.setMethod("GET");
        request.setRemoteAddr("192.168.1.100");

        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException authException = new BadCredentialsException("Token JWT ausente o inválido");

        entryPoint.commence(request, response, authException);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().contains("application/json"));

        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":\"UNAUTHORIZED\""));
        assertTrue(body.contains("\"codigo\":401"));
        assertTrue(body.contains("/api/pedidos"));
    }
}

