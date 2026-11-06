package cl.duoc.pedidos360_backend.config;

import cl.duoc.pedidos360_backend.service.OutboxService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Manejador personalizado para respuestas HTTP 403 Forbidden.
 *
 * Cumple con el requisito SEC-03 del Masterplan:
 * Captura excepciones de acceso denegado, responde con un JSON estructurado,
 * extrae el identificador del usuario ('sub' de Cognito) y emite un evento de
 * alerta de seguridad.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAccessDeniedHandler.class);

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler() {
        this(null, new ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CustomAccessDeniedHandler(
            @org.springframework.beans.factory.annotation.Autowired(required = false) OutboxService outboxService) {
        this(outboxService, new ObjectMapper());
    }

    public CustomAccessDeniedHandler(OutboxService outboxService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuarioSub = "ANONYMOUS";
        String usuarioEmail = null;

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            usuarioSub = jwt.getClaimAsString("sub");
            if (usuarioSub == null) {
                usuarioSub = jwt.getSubject();
            }
            usuarioEmail = jwt.getClaimAsString("email");
        } else if (auth != null && auth.getName() != null) {
            usuarioSub = auth.getName();
        }

        log.warn(
                "[SEGURIDAD - 403 FORBIDDEN] Intento de acceso no autorizado a '{}' [{}] por el usuario '{}'. Motivo: {}",
                request.getRequestURI(), request.getMethod(), usuarioSub, accessDeniedException.getMessage());

        // Emitir evento de alerta de seguridad hacia Outbox -> RabbitMQ
        // ('cola.seguridad.alertas')
        try {
            if (outboxService != null) {
                outboxService.registrarAlertaSeguridad(
                        usuarioSub,
                        usuarioEmail,
                        request.getRequestURI(),
                        request.getMethod(),
                        accessDeniedException.getMessage());
            }
        } catch (Exception ex) {
            log.error("[SEGURIDAD] Error al registrar evento de alerta de seguridad en Outbox: {}", ex.getMessage());
        }

        // Construir respuesta HTTP 403 Forbidden en formato JSON
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FORBIDDEN");
        body.put("codigo", HttpServletResponse.SC_FORBIDDEN);
        body.put("mensaje",
                "Acceso denegado: se requieren permisos de administrador (ROLE_Admin) para ejecutar esta operación.");
        body.put("ruta", request.getRequestURI());
        body.put("metodo", request.getMethod());
        body.put("usuario", usuarioSub);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
