package cl.duoc.pedidos360_backend.service;

import cl.duoc.pedidos360_backend.model.OutboxEvento;
import cl.duoc.pedidos360_backend.repository.OutboxEventoRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker programado encargado de publicar en RabbitMQ los eventos
 * almacenados en la tabla 'outbox_eventos' con estado PENDIENTE.
 *
 * Implementa Publisher Confirms y recuperacion automatica tras reinicios
 * para garantizar entrega At-Least-Once (Fase 4 - RAB-06).
 */
@Component
@EnableScheduling
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);
    private static final int MAX_REINTENTOS = 5;

    private final OutboxEventoRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherWorker(OutboxEventoRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Tarea programada que se ejecuta cada 5 segundos buscando eventos pendientes.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void procesarEventosPendientes() {
        List<OutboxEvento> pendientes = outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE");

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("[OUTBOX WORKER] Se encontraron {} eventos pendientes para publicar en RabbitMQ.", pendientes.size());

        for (OutboxEvento evento : pendientes) {
            publicarEvento(evento);
        }
    }

    /**
     * Publica un evento individual a RabbitMQ utilizando Publisher Confirms.
     * Solo marca PUBLICADO si el broker confirma ACK dentro del timeout.
     */
    public void publicarEvento(OutboxEvento evento) {
        try {
            CorrelationData correlationData = new CorrelationData(evento.getId().toString());

            rabbitTemplate.convertAndSend(
                    evento.getExchange(),
                    evento.getRoutingKey(),
                    evento.getPayload(),
                    message -> {
                        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        message.getMessageProperties().setMessageId(evento.getId().toString());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlationData
            );

            boolean confirmado = false;
            try {
                CorrelationData.Confirm confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
                if (confirm != null && confirm.isAck()) {
                    if (correlationData.getReturned() != null) {
                        throw new IllegalStateException("RabbitMQ no enruto el mensaje: "
                                + correlationData.getReturned().getReplyText());
                    }
                    confirmado = true;
                } else {
                    String reason = confirm != null ? confirm.getReason() : "confirm nulo";
                    throw new IllegalStateException("Broker devolvio NACK para evento: " + reason);
                }
            } catch (Exception ex) {
                log.warn("[OUTBOX WORKER] Confirmacion de broker pendiente o fallida para [{}]: {}",
                        evento.getId(), ex.getMessage());
                registrarFalla(evento, ex.getMessage());
                return;
            }

            if (confirmado) {
                evento.setEstado("PUBLICADO");
                evento.setProcesadoEn(Instant.now());
                evento.setUltimoError(null);
                outboxRepository.save(evento);

                log.info("[OUTBOX WORKER] Evento '{}' [{}] publicado exitosamente en exchange '{}' con routingKey '{}'.",
                        evento.getTipoEvento(), evento.getId(), evento.getExchange(), evento.getRoutingKey());
            }

        } catch (Exception e) {
            registrarFalla(evento, e.getMessage());
        }
    }

    private void registrarFalla(OutboxEvento evento, String mensajeError) {
        int nuevosReintentos = evento.getReintentos() + 1;
        evento.setReintentos(nuevosReintentos);
        evento.setUltimoError(mensajeError);

        if (nuevosReintentos >= MAX_REINTENTOS) {
            evento.setEstado("FALLIDO");
            log.error("[OUTBOX WORKER] Evento [{}] alcanzo el maximo de {} reintentos. Marcado como FALLIDO. Causa: {}",
                    evento.getId(), MAX_REINTENTOS, mensajeError);
        } else {
            log.warn("[OUTBOX WORKER] Falla al publicar evento [{}] (intento {}/{}). Causa: {}",
                    evento.getId(), nuevosReintentos, MAX_REINTENTOS, mensajeError);
        }

        outboxRepository.save(evento);
    }
}
