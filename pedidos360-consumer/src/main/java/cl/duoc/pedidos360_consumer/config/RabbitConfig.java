package cl.duoc.pedidos360_consumer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la topologia RabbitMQ (idempotente con el productor).
 * Garantiza que colas/bindings existan aunque el consumer arranque solo.
 */
@Configuration
public class RabbitConfig {

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
}
