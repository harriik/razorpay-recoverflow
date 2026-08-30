package com.recoverflow.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class GatewayConfig {

    @Value("${PAYMENT_GATEWAY:mock}")
    private String paymentGateway;

    @Bean
    @Primary
    public PaymentGateway paymentGateway(MockPaymentGateway mock, RazorpayPaymentGateway razorpay) {
        if ("razorpay".equalsIgnoreCase(paymentGateway)) {
            return razorpay;
        }
        return mock;
    }
}
