package cl.duoc.pedidos360_backend.repository;

import cl.duoc.pedidos360_backend.model.Pedido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de acceso a datos para la entidad Pedido.
 * Proporciona metodos CRUD completos heredados de JpaRepository.
 */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Permite consultar pedidos filtrados por su estado operativo.
     * Ejemplo: "PENDIENTE", "EN_PREPARACION", "DISPONIBLE", "ENTREGADO".
     */
    List<Pedido> findByEstado(String estado);
}
