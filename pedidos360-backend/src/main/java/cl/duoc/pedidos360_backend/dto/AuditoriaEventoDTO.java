package cl.duoc.pedidos360_backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO que representa el contrato de mensaje para eventos de auditoria.
 * Transportado en formato JSON dentro de RabbitMQ y persistido en el Outbox.
 */
public class AuditoriaEventoDTO {

    private UUID eventoId;
    private String tipoEvento;
    private String accion; // CREACION, MODIFICACION, ELIMINACION, ALERTA_SEGURIDAD
    private Long productoId;
    private String nombreProducto;
    private int cantidad;
    private int stock;
    private String estado;
    private String imageUrl;
    private String usuarioSub;
    private String usuarioEmail;
    private String fechaEvento;

    public AuditoriaEventoDTO() {
        this.eventoId = UUID.randomUUID();
        this.fechaEvento = Instant.now().toString();
    }

    public AuditoriaEventoDTO(UUID eventoId, String tipoEvento, String accion, Long productoId,
                              String nombreProducto, int cantidad, int stock, String estado,
                              String imageUrl, String usuarioSub, String usuarioEmail, String fechaEvento) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.accion = accion;
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.stock = stock;
        this.estado = estado;
        this.imageUrl = imageUrl;
        this.usuarioSub = usuarioSub;
        this.usuarioEmail = usuarioEmail;
        this.fechaEvento = fechaEvento;
    }

    // Getters y Setters

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public void setUsuarioSub(String usuarioSub) {
        this.usuarioSub = usuarioSub;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public void setUsuarioEmail(String usuarioEmail) {
        this.usuarioEmail = usuarioEmail;
    }

    public String getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(String fechaEvento) {
        this.fechaEvento = fechaEvento;
    }
}
