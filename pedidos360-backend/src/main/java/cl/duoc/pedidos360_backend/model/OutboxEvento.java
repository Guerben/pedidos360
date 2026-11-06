package cl.duoc.pedidos360_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad que implementa el patron Transactional Outbox.
 * Almacena los eventos de auditoria pendientes de publicacion
 * dentro de la misma transaccion atómica de negocio en 'pedidos360_db'.
 */
@Entity
@Table(name = "outbox_eventos")
public class OutboxEvento {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", length = 50, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 100, nullable = false)
    private String aggregateId;

    @Column(name = "tipo_evento", length = 100, nullable = false)
    private String tipoEvento;

    @Column(name = "exchange", length = 100, nullable = false)
    private String exchange;

    @Column(name = "routing_key", length = 100, nullable = false)
    private String routingKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado; // PENDIENTE, PUBLICADO, FALLIDO

    @Column(name = "reintentos", nullable = false)
    private int reintentos;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "procesado_en")
    private Instant procesadoEn;

    public OutboxEvento() {
        this.id = UUID.randomUUID();
        this.creadoEn = Instant.now();
        this.estado = "PENDIENTE";
        this.reintentos = 0;
    }

    public OutboxEvento(String aggregateType, String aggregateId, String tipoEvento,
                        String exchange, String routingKey, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.tipoEvento = tipoEvento;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.estado = "PENDIENTE";
        this.reintentos = 0;
        this.creadoEn = Instant.now();
    }

    // Getters y Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getReintentos() {
        return reintentos;
    }

    public void setReintentos(int reintentos) {
        this.reintentos = reintentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    public void setProcesadoEn(Instant procesadoEn) {
        this.procesadoEn = procesadoEn;
    }
}

