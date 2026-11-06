package cl.duoc.pedidos360_consumer.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.pedidos360_consumer.model.AuditoriaInventario;
import cl.duoc.pedidos360_consumer.repository.AuditoriaInventarioRepository;
import cl.duoc.pedidos360_consumer.repository.RabbitFailureLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventarioConsumerTest {

    @Mock
    private Channel channel;

    @Mock
    private AuditoriaInventarioRepository repository;

    @Mock
    private RabbitFailureLogRepository failureLogRepository;

    private InventarioConsumer consumer;

    @BeforeEach
    void setUp() {
        ConsumerSupport support = new ConsumerSupport(failureLogRepository);
        consumer = new InventarioConsumer(new ObjectMapper(), repository, support);
    }

    @Test
    void procesar_payloadValido_emiteAckYPersiste() throws Exception {
        UUID eventoId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        String payload = """
                {
                  "eventoId": "%s",
                  "tipoEvento": "inventario.producto.creado",
                  "accion": "CREACION",
                  "productoId": 1,
                  "nombreProducto": "Notebook",
                  "cantidad": 2,
                  "stock": 10,
                  "estado": "PENDIENTE",
                  "imageUrl": "https://example.com/img.png",
                  "usuarioSub": "sub-123",
                  "fechaEvento": "2026-11-05T12:00:00Z"
                }
                """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(false);
        when(repository.save(any(AuditoriaInventario.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.procesar(payload, channel, 7L, "evento-123");

        verify(repository).save(any(AuditoriaInventario.class));
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void procesar_forceError_emiteNackSinRequeue() throws Exception {
        String payload = "{\"eventoId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"FORCE_ERROR\":true}";

        consumer.procesar(payload, channel, 9L, "evento-456");

        verify(channel).basicNack(eq(9L), eq(false), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(repository, never()).save(any());
        verify(failureLogRepository).saveAndFlush(any());
    }
}
