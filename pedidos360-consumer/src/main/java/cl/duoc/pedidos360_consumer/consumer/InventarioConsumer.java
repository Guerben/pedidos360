package cl.duoc.pedidos360_consumer.consumer;

import cl.duoc.pedidos360_consumer.config.RabbitNames;
import cl.duoc.pedidos360_consumer.dto.AuditoriaEventoDTO;
import cl.duoc.pedidos360_consumer.model.AuditoriaInventario;
import cl.duoc.pedidos360_consumer.repository.AuditoriaInventarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class InventarioConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventarioConsumer.class);

    private final ObjectMapper objectMapper;
    private final AuditoriaInventarioRepository repository;
    private final ConsumerSupport support;

    public InventarioConsumer(ObjectMapper objectMapper,
                              AuditoriaInventarioRepository repository,
                              ConsumerSupport support) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.support = support;
    }

    @RabbitListener(queues = RabbitNames.COLA_AUDITORIA_INVENTARIO)
    public void procesar(String payload, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                         @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {

        log.info("[INVENTARIO] Mensaje recibido deliveryTag={}: {}", deliveryTag, payload);

        support.procesarConReintentos("INVENTARIO", RabbitNames.QUEUE_INVENTARIO, RabbitNames.DLQ_INVENTARIO,
                messageId, payload, deliveryTag, channel, () -> {
            if (payload == null || payload.isBlank()) {
                throw new ConsumerSupport.PermanentMessageException("Payload vacio");
            }
            if (payload.contains("\"ERROR\"") || payload.contains("FORCE_ERROR")) {
                throw new ConsumerSupport.PermanentMessageException("Payload de prueba fuerza DLQ");
            }

            AuditoriaEventoDTO dto;
            try {
                dto = objectMapper.readValue(payload, AuditoriaEventoDTO.class);
            } catch (Exception ex) {
                throw new ConsumerSupport.PermanentMessageException("JSON invalido", ex);
            }

            if (dto.getEventoId() == null) {
                throw new ConsumerSupport.PermanentMessageException("eventoId ausente");
            }

            if (repository.existsByEventoId(dto.getEventoId())) {
                log.info("[INVENTARIO] Evento duplicado {} — ACK sin insertar.", dto.getEventoId());
                return;
            }

            AuditoriaInventario entity = new AuditoriaInventario();
            entity.setEventoId(dto.getEventoId());
            entity.setTipoEvento(dto.getTipoEvento());
            entity.setAccion(dto.getAccion() != null ? dto.getAccion() : "DESCONOCIDA");
            entity.setProductoId(dto.getProductoId());
            entity.setNombreProducto(dto.getNombreProducto());
            entity.setCantidad(dto.getCantidad());
            entity.setStock(dto.getStock());
            entity.setEstado(dto.getEstado());
            entity.setImageUrl(dto.getImageUrl());
            entity.setUsuarioSub(dto.getUsuarioSub());
            entity.setUsuarioEmail(dto.getUsuarioEmail());
            entity.setFechaEvento(support.parseFecha(dto.getFechaEvento()));
            repository.save(entity);

            log.info("[INVENTARIO] Persistido evento {} productoId={}", dto.getEventoId(), dto.getProductoId());
        });
    }
}
