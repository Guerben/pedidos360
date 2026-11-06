package cl.duoc.pedidos360_consumer.repository;

import cl.duoc.pedidos360_consumer.model.HistoricoBajas;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoBajasRepository extends JpaRepository<HistoricoBajas, Long> {

    boolean existsByEventoId(UUID eventoId);
}
