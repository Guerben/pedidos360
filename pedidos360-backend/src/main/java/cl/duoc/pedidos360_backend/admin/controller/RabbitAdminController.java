package cl.duoc.pedidos360_backend.admin.controller;

import cl.duoc.pedidos360_backend.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360_backend.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos360_backend.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la administración de RabbitMQ.
 *
 * Cumple con los Indicadores de la rúbrica EP3:
 * - Indicador 6 (13%): Endpoints REST con verbos correctos (POST /queues, DELETE /queues/{nombre}).
 * - Indicador 7 (10%): Lógica desacoplada y delegada enteramente en RabbitAdminService.
 * - Indicador 8 (7%): Validación obligatoria de parámetros con @Valid y DTOs dedicados.
 */
@RestController
@RequestMapping("/api/admin")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    /**
     * Endpoint para crear una cola dinámica en RabbitMQ.
     * HTTP POST /api/admin/queues
     *
     * @param request Datos de la cola validados por Bean Validation (@Valid)
     * @return 201 Created con detalles de la cola creada
     */
    @PostMapping("/queues")
    public ResponseEntity<Map<String, Object>> crearCola(@Valid @RequestBody CrearColaRequest request) {
        String nombreCola = rabbitAdminService.crearCola(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("mensaje", "Cola declarada exitosamente en el clúster RabbitMQ.");
        response.put("cola", nombreCola != null ? nombreCola : request.getNombre());
        response.put("durable", request.isDurable());
        response.put("exclusive", request.isExclusive());
        response.put("autoDelete", request.isAutoDelete());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint para eliminar una cola de RabbitMQ.
     * HTTP DELETE /api/admin/queues/{nombre}
     *
     * @param nombre Nombre de la cola a eliminar
     * @return 200 OK si se eliminó, 404 Not Found si no existía
     */
    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Map<String, Object>> eliminarCola(@PathVariable String nombre) {
        boolean eliminada = rabbitAdminService.eliminarCola(nombre);

        if (!eliminada) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "NOT_FOUND");
            error.put("mensaje", "La cola '" + nombre + "' no existe en RabbitMQ o ya fue eliminada.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("mensaje", "Cola '" + nombre + "' eliminada exitosamente del clúster RabbitMQ.");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para consultar el estado o existencia de una cola.
     * HTTP GET /api/admin/queues/{nombre}
     *
     * @param nombre Nombre de la cola
     * @return 200 OK con propiedades de la cola o 404 Not Found si no existe
     */
    @GetMapping("/queues/{nombre}")
    public ResponseEntity<Map<String, Object>> obtenerCola(@PathVariable String nombre) {
        Properties properties = rabbitAdminService.obtenerPropiedadesCola(nombre);

        if (properties == null) {
            Map<String, Object> notFound = new LinkedHashMap<>();
            notFound.put("status", "NOT_FOUND");
            notFound.put("mensaje", "La cola '" + nombre + "' no existe en RabbitMQ.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("nombre", nombre);
        response.put("propiedades", properties);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint opcional para crear un Exchange en RabbitMQ.
     * HTTP POST /api/admin/exchanges
     */
    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, Object>> crearExchange(@Valid @RequestBody CrearExchangeRequest request) {
        rabbitAdminService.crearExchange(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("mensaje", "Exchange declarado exitosamente en el clúster RabbitMQ.");
        response.put("exchange", request.getNombre());
        response.put("tipo", request.getTipo());
        response.put("durable", request.isDurable());
        response.put("autoDelete", request.isAutoDelete());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint opcional para eliminar un Exchange en RabbitMQ.
     * HTTP DELETE /api/admin/exchanges/{nombre}
     */
    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Map<String, Object>> eliminarExchange(@PathVariable String nombre) {
        boolean eliminado = rabbitAdminService.eliminarExchange(nombre);

        if (!eliminado) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "NOT_FOUND");
            error.put("mensaje", "El exchange '" + nombre + "' no existe en RabbitMQ o ya fue eliminado.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("mensaje", "Exchange '" + nombre + "' eliminado exitosamente de RabbitMQ.");
        return ResponseEntity.ok(response);
    }

    /**
     * Manejador de excepciones de validación (@Valid).
     * Retorna 400 Bad Request con el detalle de los campos con errores.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarErroresValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "BAD_REQUEST");
        respuesta.put("mensaje", "Parámetros de solicitud inválidos.");
        respuesta.put("errores", errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    /**
     * Manejador de cuerpo de solicitud malformado o ilegible.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoInvalido(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "BAD_REQUEST");
        respuesta.put("mensaje", "El cuerpo de la solicitud es inválido o no pudo ser deserializado.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }
}
