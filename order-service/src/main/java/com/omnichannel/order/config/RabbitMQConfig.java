package com.omnichannel.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "ecommerce.exchange";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";

    public static final String ORDER_SAGA_QUEUE = "order.saga.response.queue";
    public static final String ORDER_PAYMENT_COMPLETED_QUEUE = "order.payment.completed.queue";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment.failed.queue";

    // Dead Letter Exchange and Queue for unprocessable Saga events
    public static final String DLX_NAME = "ecommerce.dlx";
    public static final String ORDER_SAGA_DLQ = "order.saga.dlq";

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue orderSagaDlq() {
        return new Queue(ORDER_SAGA_DLQ, true);
    }

    @Bean
    public Binding bindingOrderSagaDlq(Queue orderSagaDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(orderSagaDlq).to(dlxExchange).with(ORDER_SAGA_DLQ);
    }

    @Bean
    public Queue orderPaymentCompletedQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", ORDER_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue orderPaymentFailedQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", ORDER_SAGA_DLQ)
                .build();
    }

    @Bean
    public Binding bindingPaymentCompleted(Queue orderPaymentCompletedQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderPaymentCompletedQueue).to(ecommerceExchange).with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding bindingPaymentFailed(Queue orderPaymentFailedQueue, TopicExchange ecommerceExchange) {
        return BindingBuilder.bind(orderPaymentFailedQueue).to(ecommerceExchange).with(PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper typeMapper = new org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
