package cl.duoc.pedidos360_consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historico_bajas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_historico_bajas_evento", columnNames = "evento_id")
})
public class HistoricoBajas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "nombre_producto", length = 255)
    private String nombreProducto;

    @Column(name = "cantidad")
    private int cantidad;

    @Column(name = "stock")
    private int stock;

    @Column(name = "estado", length = 50)
    private String estado;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "usuario_sub", length = 100)
    private String usuarioSub;

    @Column(name = "usuario_email", length = 255)
    private String usuarioEmail;

    @Column(name = "fecha_evento")
    private Instant fechaEvento;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn = Instant.now();

    public Long getId() {
        return id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
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

    public Instant getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(Instant fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }

    public void setRecibidoEn(Instant recibidoEn) {
        this.recibidoEn = recibidoEn;
    }
}
