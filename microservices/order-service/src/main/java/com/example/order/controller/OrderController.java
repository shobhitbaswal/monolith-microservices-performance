
package com.example.order.controller;

import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final RestClient restClient;
    private final String catalogBaseUrl;
    private final String cartBaseUrl;

    public OrderController(
            OrderRepository orderRepository,
            @Value("${services.catalog.base-url:http://localhost:8081}") String catalogBaseUrl,
            @Value("${services.cart.base-url:http://localhost:8083}") String cartBaseUrl,
            @Value("${services.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.http.read-timeout:5s}") Duration readTimeout) {

        this.orderRepository = orderRepository;
        this.catalogBaseUrl = catalogBaseUrl;
        this.cartBaseUrl = cartBaseUrl;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @GetMapping
    public List<Order> listOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    // Matches the monolith's user-order listing behavior.
    @GetMapping("/user/{userId}")
    public List<Order> getUserOrders(@PathVariable Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @PostMapping
    public Order createOrder(@RequestBody OrderRequest request) {
        if (request.userId() == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "userId and at least one item are required");
        }

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemInput item : request.items()) {
            if (item.productId() == null
                    || item.quantity() == null
                    || item.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Each item needs a productId and a positive quantity");
            }

            ProductResponse product = restClient.get()
                    .uri(catalogBaseUrl + "/products/" + item.productId())
                    .retrieve()
                    .body(ProductResponse.class);

            if (product == null || product.price() == null) {
                throw new IllegalStateException(
                        "Could not retrieve product price");
            }

            total = total.add(
                    product.price().multiply(
                            BigDecimal.valueOf(item.quantity()))
            );
        }

        Order order = new Order();
        order.setUserId(request.userId());
        order.setStatus("PAID");
        order.setTotalPrice(total);
        order.setOrderDate(Instant.now());

        Order saved = orderRepository.save(order);

        // Match the existing cart-clearing behavior.
        restClient.delete()
                .uri(cartBaseUrl + "/cart/" + request.userId())
                .retrieve()
                .toBodilessEntity();

        return saved;
    }

    public record OrderRequest(Long userId, List<OrderItemInput> items) {}

    public record OrderItemInput(Long productId, Integer quantity) {}

    public record ProductResponse(Long id, BigDecimal price) {}
}
