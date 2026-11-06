package cl.duoc.pedidos360_consumer.repository;

import cl.duoc.pedidos360_consumer.model.AuditoriaSeguridad;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaSeguridadRepository extends JpaRepository<AuditoriaSeguridad, Long> {

    boolean existsByEventoId(UUID eventoId);
}
