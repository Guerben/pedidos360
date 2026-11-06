package cl.duoc.pedidos360_backend.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.pedidos360_backend.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360_backend.admin.dto.CrearExchangeRequest;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    private RabbitAdminService rabbitAdminService;

    @BeforeEach
    void setUp() {
        rabbitAdminService = new RabbitAdminService(amqpAdmin);
    }

    @Test
    void crearCola_declaraColaEnAmqpAdmin() {
        CrearColaRequest request = new CrearColaRequest("cola.test.dinamica", true, false, false);
        when(amqpAdmin.declareQueue(any(Queue.class))).thenReturn("cola.test.dinamica");

        String nombreDeclarado = rabbitAdminService.crearCola(request);

        assertEquals("cola.test.dinamica", nombreDeclarado);
        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue q = captor.getValue();
        assertEquals("cola.test.dinamica", q.getName());
        assertTrue(q.isDurable());
        assertFalse(q.isExclusive());
        assertFalse(q.isAutoDelete());
    }

    @Test
    void eliminarCola_cuandoExiste_retornaTrue() {
        when(amqpAdmin.deleteQueue("cola.test.eliminar")).thenReturn(true);

        boolean resultado = rabbitAdminService.eliminarCola("cola.test.eliminar");

        assertTrue(resultado);
        verify(amqpAdmin).deleteQueue("cola.test.eliminar");
    }

    @Test
    void eliminarCola_cuandoNoExiste_retornaFalse() {
        when(amqpAdmin.deleteQueue("cola.inexistente")).thenReturn(false);

        boolean resultado = rabbitAdminService.eliminarCola("cola.inexistente");

        assertFalse(resultado);
        verify(amqpAdmin).deleteQueue("cola.inexistente");
    }

    @Test
    void existeCola_retornaBooleanoSegunPropiedades() {
        Properties props = new Properties();
        props.put("QUEUE_MESSAGE_COUNT", 0);
        when(amqpAdmin.getQueueProperties("cola.existente")).thenReturn(props);
        when(amqpAdmin.getQueueProperties("cola.inexistente")).thenReturn(null);

        assertTrue(rabbitAdminService.existeCola("cola.existente"));
        assertFalse(rabbitAdminService.existeCola("cola.inexistente"));
    }

    @Test
    void crearExchange_declaraExchangeSegunTipo() {
        CrearExchangeRequest request = new CrearExchangeRequest("test.exchange.topic", "topic", true, false);

        rabbitAdminService.crearExchange(request);

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        Exchange ex = captor.getValue();
        assertEquals("test.exchange.topic", ex.getName());
        assertEquals("topic", ex.getType());
        assertTrue(ex.isDurable());
    }
}

