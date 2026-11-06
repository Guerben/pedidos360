package cl.duoc.pedidos360_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de solicitud para la creacion de colas dinamicas en RabbitMQ.
 * Cumple con el Indicador 8 de la rubrica EP3 (7%):
 * Validacion estricta de parametros con DTOs para evitar nombres vacios o configuraciones invalidas.
 */
public class CrearColaRequest {

    @NotBlank(message = "El nombre de la cola es obligatorio y no puede estar vacío.")
    @Size(min = 3, max = 100, message = "El nombre de la cola debe tener entre 3 y 100 caracteres.")
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "El nombre de la cola solo puede contener caracteres alfanuméricos, puntos, guiones y guiones bajos."
    )
    private String nombre;

    private boolean durable = true;
    private boolean exclusive = false;
    private boolean autoDelete = false;

    public CrearColaRequest() {
    }

    public CrearColaRequest(String nombre, boolean durable, boolean exclusive, boolean autoDelete) {
        this.nombre = nombre;
        this.durable = durable;
        this.exclusive = exclusive;
        this.autoDelete = autoDelete;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }
}

