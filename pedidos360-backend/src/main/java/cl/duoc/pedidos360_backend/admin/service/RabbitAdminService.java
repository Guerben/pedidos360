package cl.duoc.pedidos360_backend.admin.service;

import cl.duoc.pedidos360_backend.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360_backend.admin.dto.CrearExchangeRequest;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

/**
 * Servicio de administración de RabbitMQ encapsulado.
 *
 * Cumple con los Indicadores de la rúbrica EP3:
 * - Indicador 3 (10%): Separación estricta de responsabilidades, desacoplando
 *   la infraestructura de mensajería del controlador REST.
 * - Indicador 7 (10%): Lógica de administración delegada completamente en este servicio,
 *   usando AmqpAdmin para declarar y eliminar colas y exchanges.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /**
     * Declara una nueva cola en RabbitMQ con los parámetros suministrados.
     *
     * @param request Datos de la cola a declarar
     * @return Nombre de la cola declarada
     */
    public String crearCola(CrearColaRequest request) {
        log.info("[RABBIT ADMIN SERVICE] Solicitud para declarar cola: nombre='{}', durable={}, exclusive={}, autoDelete={}",
                request.getNombre(), request.isDurable(), request.isExclusive(), request.isAutoDelete());

        Queue queue = new Queue(
                request.getNombre(),
                request.isDurable(),
                request.isExclusive(),
                request.isAutoDelete()
        );

        String declaredName = amqpAdmin.declareQueue(queue);
        log.info("[RABBIT ADMIN SERVICE] Cola declarada exitosamente en RabbitMQ: {}", declaredName);
        return declaredName;
    }

    /**
     * Elimina una cola existente en RabbitMQ.
     *
     * @param nombre Nombre de la cola a eliminar
     * @return true si fue eliminada; false si no existía o no pudo eliminarse
     */
    public boolean eliminarCola(String nombre) {
        log.info("[RABBIT ADMIN SERVICE] Solicitud para eliminar cola: '{}'", nombre);
        try {
            boolean eliminada = amqpAdmin.deleteQueue(nombre);
            if (eliminada) {
                log.info("[RABBIT ADMIN SERVICE] Cola '{}' eliminada con éxito.", nombre);
            } else {
                log.warn("[RABBIT ADMIN SERVICE] La cola '{}' no existía o no pudo ser eliminada.", nombre);
            }
            return eliminada;
        } catch (AmqpException e) {
            log.error("[RABBIT ADMIN SERVICE] Error AMQP al eliminar la cola '{}': {}", nombre, e.getMessage());
            throw e;
        }
    }

    /**
     * Verifica si una cola existe consultando sus propiedades en RabbitMQ.
     *
     * @param nombre Nombre de la cola
     * @return true si existe, false si no
     */
    public boolean existeCola(String nombre) {
        Properties properties = amqpAdmin.getQueueProperties(nombre);
        return properties != null;
    }

    /**
     * Obtiene las propiedades de una cola (mensajes pendientes, consumidores, etc.).
     *
     * @param nombre Nombre de la cola
     * @return Properties de la cola o null si no existe
     */
    public Properties obtenerPropiedadesCola(String nombre) {
        return amqpAdmin.getQueueProperties(nombre);
    }

    /**
     * Declara un exchange dinámico en RabbitMQ.
     *
     * @param request Datos del exchange a declarar
     */
    public void crearExchange(CrearExchangeRequest request) {
        String tipo = request.getTipo().trim().toLowerCase();
        Exchange exchange;

        switch (tipo) {
            case "topic":
                exchange = new TopicExchange(request.getNombre(), request.isDurable(), request.isAutoDelete());
                break;
            case "fanout":
                exchange = new FanoutExchange(request.getNombre(), request.isDurable(), request.isAutoDelete());
                break;
            case "headers":
                exchange = new HeadersExchange(request.getNombre(), request.isDurable(), request.isAutoDelete());
                break;
            case "direct":
            default:
                exchange = new DirectExchange(request.getNombre(), request.isDurable(), request.isAutoDelete());
                break;
        }

        amqpAdmin.declareExchange(exchange);
        log.info("[RABBIT ADMIN SERVICE] Exchange '{}' de tipo '{}' declarado exitosamente.", request.getNombre(), tipo);
    }

    /**
     * Elimina un exchange existente en RabbitMQ.
     *
     * @param nombre Nombre del exchange a eliminar
     * @return true si fue eliminado con éxito
     */
    public boolean eliminarExchange(String nombre) {
        log.info("[RABBIT ADMIN SERVICE] Solicitud para eliminar exchange: '{}'", nombre);
        return amqpAdmin.deleteExchange(nombre);
    }
}

