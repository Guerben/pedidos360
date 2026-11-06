package cl.duoc.pedidos360_backend.repository;

import cl.duoc.pedidos360_backend.model.OutboxEvento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, UUID> {

    /**
     * Recupera los eventos pendientes de publicacion ordenados cronologicamente.
     */
    List<OutboxEvento> findByEstadoOrderByCreadoEnAsc(String estado);

    /**
     * Recupera un lote de eventos pendientes para evitar sobrecarga de memoria.
     */
    List<OutboxEvento> findTop50ByEstadoOrderByCreadoEnAsc(String estado);
}

