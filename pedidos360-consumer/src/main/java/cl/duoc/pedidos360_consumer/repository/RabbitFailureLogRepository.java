package cl.duoc.pedidos360_consumer.repository;

import cl.duoc.pedidos360_consumer.model.RabbitFailureLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RabbitFailureLogRepository extends JpaRepository<RabbitFailureLog, Long> {

    Optional<RabbitFailureLog> findByMessageId(String messageId);
}
