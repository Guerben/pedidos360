package cl.duoc.pedidos360_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de solicitud para la creación dinámica de exchanges en RabbitMQ.
 * Cumple con el requisito ADM-01 e Indicador 8 de la rúbrica EP3.
 */
public class CrearExchangeRequest {

    @NotBlank(message = "El nombre del exchange es obligatorio.")
    @Size(min = 3, max = 100, message = "El nombre del exchange debe tener entre 3 y 100 caracteres.")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El nombre del exchange solo puede contener caracteres alfanuméricos, puntos, guiones y guiones bajos."
    )
    private String nombre;

    @NotBlank(message = "El tipo de exchange es obligatorio (direct, topic, fanout, headers).")
    @Pattern(
        regexp = "^(direct|topic|fanout|headers)$",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "El tipo de exchange debe ser uno de: direct, topic, fanout, headers."
    )
    private String tipo = "direct";

    private Boolean durable = true;
    private Boolean autoDelete = false;

    public CrearExchangeRequest() {
    }

    public CrearExchangeRequest(String nombre, String tipo, Boolean durable, Boolean autoDelete) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.durable = durable != null ? durable : true;
        this.autoDelete = autoDelete != null ? autoDelete : false;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public boolean isDurable() {
        return Boolean.TRUE.equals(durable);
    }

    public void setDurable(Boolean durable) {
        this.durable = durable;
    }

    public boolean isAutoDelete() {
        return Boolean.TRUE.equals(autoDelete);
    }

    public void setAutoDelete(Boolean autoDelete) {
        this.autoDelete = autoDelete;
    }
}
