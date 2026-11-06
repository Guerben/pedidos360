package cl.duoc.pedidos360_backend.service;

import cl.duoc.pedidos360_backend.config.RabbitNames;
import cl.duoc.pedidos360_backend.exception.ResourceNotFoundException;
import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.repository.PedidoRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capa de servicio encargada de la logica de negocio para la gestion de
 * Pedidos.
 * Integra el patron Transactional Outbox para asegurar que todo cambio en el
 * catalogo
 * genere un evento de auditoria persistido de forma atomica en la misma
 * transaccion ACID.
 */
@Service
public class PedidoService {

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "PENDIENTE", "EN_PREPARACION", "DISPONIBLE", "ENTREGADO");

    private final PedidoRepository pedidoRepository;
    private final OutboxService outboxService;

    public PedidoService(PedidoRepository pedidoRepository, OutboxService outboxService) {
        this.pedidoRepository = pedidoRepository;
        this.outboxService = outboxService;
    }

    /**
     * Obtiene el listado completo de pedidos registrados en el sistema.
     */
    @Transactional(readOnly = true)
    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    /**
     * Busca un pedido especifico por su identificador unico.
     */
    @Transactional(readOnly = true)
    public Pedido obtenerPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));
    }

    /**
     * Registra un nuevo pedido en la base de datos y genera el evento de auditoria
     * en la tabla Outbox de forma atomica dentro de la misma transaccion.
     */
    @Transactional
    public Pedido crearPedido(Pedido pedido) {
        if (pedido.getImageUrl() == null || pedido.getImageUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("La URL de la imagen (imageUrl) es obligatoria y no puede estar vacía.");
        }

        if (pedido.getEstado() == null || pedido.getEstado().trim().isEmpty()) {
            pedido.setEstado("PENDIENTE");
        }
        pedido.setEstado(validarEstado(pedido.getEstado()));

        // 1. Guardar el nuevo pedido en la tabla 'pedidos'
        Pedido nuevoPedido = pedidoRepository.save(pedido);

        // 2. Registrar evento en outbox_eventos dentro de la misma transaccion
        outboxService.registrarEventoInventario(
                nuevoPedido,
                "CREACION",
                RabbitNames.RK_PRODUCTO_CREADO);

        return nuevoPedido;
    }

    /**
     * Actualiza la informacion de un pedido existente y registra el evento
     * correspondiente en la tabla Outbox.
     */
    @Transactional
    public Pedido actualizarPedido(Long id, Pedido detalles) {
        Pedido pedidoExistente = obtenerPorId(id);

        if (detalles.getProducto() != null && !detalles.getProducto().trim().isEmpty()) {
            pedidoExistente.setProducto(detalles.getProducto().trim());
        }

        if (detalles.getImageUrl() != null && !detalles.getImageUrl().trim().isEmpty()) {
            pedidoExistente.setImageUrl(detalles.getImageUrl().trim());
        }

        pedidoExistente.setCantidad(detalles.getCantidad());
        pedidoExistente.setStock(detalles.getStock());

        if (detalles.getEstado() != null && !detalles.getEstado().trim().isEmpty()) {
            pedidoExistente.setEstado(validarEstado(detalles.getEstado()));
        }

        // 1. Persistir cambios en 'pedidos'
        Pedido pedidoActualizado = pedidoRepository.save(pedidoExistente);

        // 2. Registrar evento en outbox_eventos dentro de la misma transaccion
        outboxService.registrarEventoInventario(
                pedidoActualizado,
                "MODIFICACION",
                RabbitNames.RK_PRODUCTO_MODIFICADO);

        return pedidoActualizado;
    }

    /**
     * Elimina un pedido del sistema.
     * Captura la instantanea completa del producto antes de su eliminacion fisica
     * para que el registro de auditoria conserve los datos historicos completos.
     */
    @Transactional
    public void eliminarPedido(Long id) {
        // 1. Obtener entidad existente en memoria (Snapshot previo)
        Pedido pedidoExistente = obtenerPorId(id);

        // 2. Registrar el evento en Outbox con todos los datos del producto
        outboxService.registrarEventoEliminacion(pedidoExistente);

        // 3. Ejecutar la eliminacion fisica en la tabla 'pedidos'
        pedidoRepository.delete(pedidoExistente);
    }

    private String validarEstado(String estado) {
        String estadoNormalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS_VALIDOS.contains(estadoNormalizado)) {
            throw new IllegalArgumentException("Estado no válido. Valores permitidos: " + ESTADOS_VALIDOS);
        }
        return estadoNormalizado;
    }
}
