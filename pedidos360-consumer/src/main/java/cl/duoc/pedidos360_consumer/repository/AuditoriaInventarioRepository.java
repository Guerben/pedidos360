package cl.duoc.pedidos360_consumer.repository;

import cl.duoc.pedidos360_consumer.model.AuditoriaInventario;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaInventarioRepository extends JpaRepository<AuditoriaInventario, Long> {

    boolean existsByEventoId(UUID eventoId);
}
