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
@Table(name = "auditoria_seguridad", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auditoria_seguridad_evento", columnNames = "evento_id")
})
public class AuditoriaSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "tipo_evento", length = 100, nullable = false)
    private String tipoEvento;

    @Column(name = "usuario_sub", length = 100)
    private String usuarioSub;

    @Column(name = "usuario_email", length = 255)
    private String usuarioEmail;

    @Column(name = "ruta", length = 512)
    private String ruta;

    @Column(name = "metodo", length = 20)
    private String metodo;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

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

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
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

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
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
