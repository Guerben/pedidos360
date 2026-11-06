package cl.duoc.pedidos360_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import cl.duoc.pedidos360_backend.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CustomAccessDeniedHandlerTest {

    @Mock
    private OutboxService outboxService;

    private ObjectMapper objectMapper;
    private CustomAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new CustomAccessDeniedHandler(outboxService, objectMapper);
    }

    @Test
    void handle_retornaHttp403YEmitioAlertaOutbox() throws Exception {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "usuario-intruso-uuid")
                .claim("email", "intruso@duocuc.cl")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "usuario-intruso-uuid");
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/pedidos/1");
        request.setMethod("DELETE");
        request.setRemoteAddr("10.0.0.5");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException accessDeniedException = new AccessDeniedException("Acceso denegado: falta rol Admin");

        handler.handle(request, response, accessDeniedException);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().contains("application/json"));

        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":\"FORBIDDEN\""));
        assertTrue(body.contains("\"codigo\":403"));
        assertTrue(body.contains("usuario-intruso-uuid"));

        verify(outboxService).registrarAlertaSeguridad(
                eq("usuario-intruso-uuid"),
                eq("intruso@duocuc.cl"),
                eq("/api/pedidos/1"),
                eq("DELETE"),
                anyString()
        );

        SecurityContextHolder.clearContext();
    }
}

