package cl.duoc.pedidos360_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada personalizado para respuestas HTTP 401 Unauthorized.
 *
 * Cumple con el requisito SEC-04 del Masterplan:
 * Captura solicitudes no autenticadas o con token JWT inválido/expirado,
 * registra el intento en los logs y responde con un JSON uniforme.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint() {
        this.objectMapper = new ObjectMapper();
    }

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("[SEGURIDAD - 401 UNAUTHORIZED] Acceso no autenticado a '{}' [{}] desde IP '{}'. Motivo: {}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UNAUTHORIZED");
        body.put("codigo", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("mensaje", "No autenticado: se requiere un token Bearer JWT válido emitido por AWS Cognito para acceder a este recurso.");
        body.put("ruta", request.getRequestURI());
        body.put("metodo", request.getMethod());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
