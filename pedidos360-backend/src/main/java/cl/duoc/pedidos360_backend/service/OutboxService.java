package cl.duoc.pedidos360_backend.service;

import cl.duoc.pedidos360_backend.config.RabbitNames;
import cl.duoc.pedidos360_backend.dto.AuditoriaEventoDTO;
import cl.duoc.pedidos360_backend.model.OutboxEvento;
import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.repository.OutboxEventoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio encargado de gestionar el patron Transactional Outbox.
 * Registra los eventos de auditoria dentro de la misma transaccion ACID
 * de la operacion de inventario, garantizando entrega At-Least-Once.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventoRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Registra un evento de inventario (creacion o modificacion) en la tabla Outbox.
     * Participa en la transaccion activa del metodo llamador (MANDATORY o REQUIRED).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarEventoInventario(Pedido pedido, String accion, String routingKey) {
        UUID eventoId = UUID.randomUUID();
        String usuarioSub = obtenerUsuarioSubActual();
        String usuarioEmail = obtenerUsuarioEmailActual();

        AuditoriaEventoDTO dto = new AuditoriaEventoDTO(
                eventoId,
                routingKey,
                accion,
                pedido.getId(),
                pedido.getProducto(),
                pedido.getCantidad(),
                pedido.getStock(),
                pedido.getEstado(),
                pedido.getImageUrl(),
                usuarioSub,
                usuarioEmail,
                Instant.now().toString()
        );

        guardarEnOutbox(
                "PRODUCTO",
                String.valueOf(pedido.getId()),
                routingKey,
                RabbitNames.TOPIC_EXCHANGE,
                routingKey,
                dto
        );
    }

    /**
     * Registra el evento de baja/eliminacion preservando la instantanea completa
     * del producto antes de que sea eliminado fisicamente de la tabla 'pedidos'.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarEventoEliminacion(Pedido pedidoSnapshot) {
        UUID eventoId = UUID.randomUUID();
        String usuarioSub = obtenerUsuarioSubActual();
        String usuarioEmail = obtenerUsuarioEmailActual();

        AuditoriaEventoDTO dto = new AuditoriaEventoDTO(
                eventoId,
                RabbitNames.RK_PRODUCTO_ELIMINADO,
                "ELIMINACION",
                pedidoSnapshot.getId(),
                pedidoSnapshot.getProducto(),
                pedidoSnapshot.getCantidad(),
                pedidoSnapshot.getStock(),
                pedidoSnapshot.getEstado(),
                pedidoSnapshot.getImageUrl(),
                usuarioSub,
                usuarioEmail,
                Instant.now().toString()
        );

        guardarEnOutbox(
                "PRODUCTO",
                String.valueOf(pedidoSnapshot.getId()),
                RabbitNames.RK_PRODUCTO_ELIMINADO,
                RabbitNames.TOPIC_EXCHANGE,
                RabbitNames.RK_PRODUCTO_ELIMINADO,
                dto
        );
    }

    /**
     * Registra una alerta de seguridad (por ejemplo HTTP 403 Forbidden por intento de mutación no autorizada)
     * en el Direct Exchange 'pedidos.seguridad.direct' con routing key 'seguridad.acceso_denegado'.
     * Utiliza REQUIRES_NEW para garantizar persistencia independiente de la transacción HTTP.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAlertaSeguridad(String usuarioSub, String usuarioEmail, String ruta, String metodo, String motivo) {
        UUID eventoId = UUID.randomUUID();

        java.util.Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
        payloadMap.put("eventoId", eventoId.toString());
        payloadMap.put("tipoEvento", "ALERTA_ACCESO_DENEGADO");
        payloadMap.put("usuarioSub", usuarioSub != null ? usuarioSub : "ANONYMOUS");
        if (usuarioEmail != null) {
            payloadMap.put("usuarioEmail", usuarioEmail);
        }
        payloadMap.put("ruta", ruta);
        payloadMap.put("metodo", metodo);
        payloadMap.put("motivo", motivo);
        payloadMap.put("fechaEvento", Instant.now().toString());

        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            OutboxEvento outboxEvento = new OutboxEvento(
                    "SEGURIDAD",
                    usuarioSub != null ? usuarioSub : "ANONYMOUS",
                    "ALERTA_ACCESO_DENEGADO",
                    RabbitNames.DIRECT_EXCHANGE,
                    RabbitNames.RK_SEGURIDAD,
                    jsonPayload
            );
            outboxEvento.setId(eventoId);
            outboxRepository.save(outboxEvento);
            log.info("[OUTBOX] Alerta de seguridad registrada en outbox para usuario '{}'. ID: {}", usuarioSub, eventoId);
        } catch (JsonProcessingException e) {
            log.error("[OUTBOX] Error al serializar alerta de seguridad a JSON: {}", e.getMessage(), e);
        }
    }

    private void guardarEnOutbox(String aggregateType, String aggregateId, String tipoEvento,
                                String exchange, String routingKey, AuditoriaEventoDTO dto) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(dto);
            OutboxEvento outboxEvento = new OutboxEvento(
                    aggregateType,
                    aggregateId,
                    tipoEvento,
                    exchange,
                    routingKey,
                    jsonPayload
            );
            // Asegura que el ID coincida con el eventoId para trazabilidad exacta
            outboxEvento.setId(dto.getEventoId());
            outboxRepository.save(outboxEvento);
            log.info("[OUTBOX] Evento '{}' registrado exitosamente en base de datos. ID: {}", tipoEvento, dto.getEventoId());
        } catch (JsonProcessingException e) {
            log.error("[OUTBOX] Error al serializar payload de auditoria a JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Error al serializar evento outbox", e);
        }
    }

    private String obtenerUsuarioSubActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "SISTEMA";
    }

    private String obtenerUsuarioEmailActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.hasClaim("email")) {
            return jwt.getClaimAsString("email");
        }
        return null;
    }
}
