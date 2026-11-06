package cl.duoc.pedidos360_backend.config;

import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializador de datos de prueba para asegurar que el Backoffice
 * disponga de pedidos precargados al iniciar en desarrollo o en EC2.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(PedidoRepository pedidoRepository) {
        return args -> {
            try {
                if (pedidoRepository.count() == 0) {
                    log.info("Cargando datos iniciales de pedidos en PostgreSQL...");

                    pedidoRepository.save(new Pedido(
                            "Notebook Lenovo ThinkPad",
                            "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=800&q=80",
                            2,
                            15,
                            "EN_PREPARACION"
                    ));

                    pedidoRepository.save(new Pedido(
                            "Smartphone Samsung Galaxy",
                            "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=800&q=80",
                            1,
                            30,
                            "DISPONIBLE"
                    ));

                    pedidoRepository.save(new Pedido(
                            "Auriculares Sony WH-1000XM4",
                            "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80",
                            3,
                            20,
                            "ENTREGADO"
                    ));

                    log.info("Se precargaron {} pedidos iniciales correctamente.", pedidoRepository.count());
                }
            } catch (Exception e) {
                log.warn("[DataInitializer] Base de datos no disponible durante inicializacion de datos: {}", e.getMessage());
            }
        };
    }
}
