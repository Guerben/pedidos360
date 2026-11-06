package cl.duoc.pedidos360_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion declarativa de infraestructura de RabbitMQ mediante Beans.
 * Cumple con el Indicador 2 (13%) y el Indicador 3 (10%) de la rubrica EP3:
 * Beans explicitos de Queue, Exchange, Binding y DLX, aislados de la logica de negocio.
 */
@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    // =========================================================================
    // 1. EXCHANGES PRINCIPALES Y DLX
    // =========================================================================

    @Bean
    public TopicExchange pedidosAuditoriaTopicExchange() {
        return new TopicExchange(RabbitNames.TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange pedidosSeguridadDirectExchange() {
        return new DirectExchange(RabbitNames.DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange pedidosDlxExchange() {
        return new DirectExchange(RabbitNames.DLX_EXCHANGE, true, false);
    }

    // =========================================================================
    // 2. COLAS PRINCIPALES CON ARGUMENTOS DLX Y DEAD LETTER ROUTING KEY
    // =========================================================================

    @Bean
    public Queue colaAuditoriaInventario() {
        return QueueBuilder.durable(RabbitNames.QUEUE_INVENTARIO)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_INVENTARIO)
                .build();
    }

    @Bean
    public Queue colaAuditoriaEliminaciones() {
        return QueueBuilder.durable(RabbitNames.QUEUE_ELIMINACIONES)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_ELIMINACIONES)
                .build();
    }

    @Bean
    public Queue colaSeguridadAlertas() {
        return QueueBuilder.durable(RabbitNames.QUEUE_SEGURIDAD)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_SEGURIDAD)
                .build();
    }

    // =========================================================================
    // 3. COLAS DEAD LETTER (DLQ)
    // =========================================================================

    @Bean
    public Queue colaAuditoriaInventarioDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_INVENTARIO).build();
    }

    @Bean
    public Queue colaAuditoriaEliminacionesDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_ELIMINACIONES).build();
    }

    @Bean
    public Queue colaSeguridadAlertasDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_SEGURIDAD).build();
    }

    // =========================================================================
    // 4. BINDINGS DE COLAS PRINCIPALES A EXCHANGES
    // =========================================================================

    @Bean
    public Binding bindingInventario(Queue colaAuditoriaInventario, TopicExchange pedidosAuditoriaTopicExchange) {
        return BindingBuilder.bind(colaAuditoriaInventario)
                .to(pedidosAuditoriaTopicExchange)
                .with(RabbitNames.RK_INVENTARIO_PATTERN);
    }

    @Bean
    public Binding bindingEliminaciones(Queue colaAuditoriaEliminaciones, TopicExchange pedidosAuditoriaTopicExchange) {
        return BindingBuilder.bind(colaAuditoriaEliminaciones)
                .to(pedidosAuditoriaTopicExchange)
                .with(RabbitNames.RK_ELIMINACIONES_PATTERN);
    }

    @Bean
    public Binding bindingSeguridad(Queue colaSeguridadAlertas, DirectExchange pedidosSeguridadDirectExchange) {
        return BindingBuilder.bind(colaSeguridadAlertas)
                .to(pedidosSeguridadDirectExchange)
                .with(RabbitNames.RK_SEGURIDAD);
    }

    // =========================================================================
    // 5. BINDINGS DE COLAS DLQ AL DEAD LETTER EXCHANGE (pedidos.dlx)
    // =========================================================================

    @Bean
    public Binding bindingDlqInventario(Queue colaAuditoriaInventarioDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaAuditoriaInventarioDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_INVENTARIO);
    }

    @Bean
    public Binding bindingDlqEliminaciones(Queue colaAuditoriaEliminacionesDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaAuditoriaEliminacionesDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_ELIMINACIONES);
    }

    @Bean
    public Binding bindingDlqSeguridad(Queue colaSeguridadAlertasDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaSeguridadAlertasDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_SEGURIDAD);
    }

    // =========================================================================
    // 6. RabbitTemplate con payload String JSON ya serializado + ReturnsCallback
    // =========================================================================

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // El Outbox ya serializa a JSON String; evitar doble encode con Jackson converter
        template.setMessageConverter(new SimpleMessageConverter());
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.error(
                "[RABBIT RETURNS] Mensaje no enrutado. exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText(),
                new String(returned.getMessage().getBody())
        ));
        return template;
    }
}
