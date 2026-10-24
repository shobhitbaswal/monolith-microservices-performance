package com.example.order.controller;

import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the order service. Catalog and cart are real HTTP servers on
 * loopback (JDK HttpServer), so inter-service calls, timeouts and error handling are
 * exercised over real sockets. The repository is a Mockito mock: PostgreSQL persistence
 * is covered by tests/equivalence/run-equivalence.mjs, not here.
 */
class OrderControllerTest {

    private static final int SLOW_PRODUCT = 2;
    private static final long CART_FAILS_FOR_USER = 13L;

    private HttpServer fake;
    private String baseUrl;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private OrderRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        repo = mock(OrderRepository.class);
        when(repo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        fake = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        fake.setExecutor(Executors.newCachedThreadPool());
        fake.createContext("/", this::handle);
        fake.start();
        baseUrl = "http://127.0.0.1:" + fake.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        fake.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        requests.add(ex.getRequestMethod() + " " + path);
        try {
            if (path.equals("/products/1")) {
                json(ex, 200, "{\"id\":1,\"name\":\"Laptop\",\"price\":999.99,\"stock\":50}");
            } else if (path.equals("/products/" + SLOW_PRODUCT)) {
                Thread.sleep(1500);
                json(ex, 200, "{\"id\":2,\"price\":29.99}");
            } else if (path.startsWith("/products/")) {
                json(ex, 500, "{\"status\":500}"); // mirrors catalog's orElseThrow -> 500
            } else if (path.startsWith("/cart/") && "DELETE".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(path.equals("/cart/" + CART_FAILS_FOR_USER) ? 500 : 200, -1);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.close();
        }
    }

    private static void json(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
    }

    private OrderController controller(Duration readTimeout) {
        return new OrderController(
                repo,
                baseUrl,
                baseUrl,
                Duration.ofSeconds(2),
                readTimeout
        );
    }

    private static OrderController.OrderRequest request(Long userId, Long productId, Integer qty) {
        return new OrderController.OrderRequest(userId,
                List.of(new OrderController.OrderItemInput(productId, qty)));
    }

    // ---- checkout ----

    @Test
    void successfulCheckoutUsesCatalogPriceMarksPaidAndClearsCart() {
        OrderController c = controller(Duration.ofSeconds(5));
        Order o = c.createOrder(request(7L, 1L, 2));

        assertThat(o.getTotalPrice()).isEqualByComparingTo(new BigDecimal("1999.98"));
        assertThat(o.getStatus()).isEqualTo("PAID");
        assertThat(o.getUserId()).isEqualTo(7L);
        assertThat(requests).contains("GET /products/1", "DELETE /cart/7");
        verify(repo, times(1)).save(any(Order.class));
    }

    @Test
    void missingProductFailsWithoutPersistingOrOrClearingCart() {
        OrderController c = controller(Duration.ofSeconds(5));
        assertThrows(HttpServerErrorException.class, () -> c.createOrder(request(7L, 99L, 1)));

        verify(repo, never()).save(any(Order.class));
        assertThat(requests).doesNotContain("DELETE /cart/7");
    }

    @Test
    void invalidInputIsRejectedBeforeAnyWork() {
        OrderController c = controller(Duration.ofSeconds(5));
        assertThrows(IllegalArgumentException.class, () -> c.createOrder(request(null, 1L, 1)));
        assertThrows(IllegalArgumentException.class, () -> c.createOrder(request(7L, null, 1)));
        assertThrows(IllegalArgumentException.class, () -> c.createOrder(request(7L, 1L, 0)));
        assertThrows(IllegalArgumentException.class, () -> c.createOrder(request(7L, 1L, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> c.createOrder(new OrderController.OrderRequest(7L, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> c.createOrder(new OrderController.OrderRequest(7L, null)));

        verify(repo, never()).save(any(Order.class));
        assertThat(requests).isEmpty();
    }

    // ---- dependency failure ----

    @Test
    void slowCatalogTriggersReadTimeoutAndNothingIsPersisted() {
        OrderController c = controller(Duration.ofMillis(300));
        assertThrows(ResourceAccessException.class, () -> c.createOrder(request(7L, (long) SLOW_PRODUCT, 1)));

        verify(repo, never()).save(any(Order.class));
    }

    /** Documents the known distributed-checkout difference: the monolith rolls back, we do not. */
    @Test
    void cartClearFailureLeavesOrderPersisted() {
        OrderController c = controller(Duration.ofSeconds(5));
        assertThrows(HttpServerErrorException.class, () -> c.createOrder(request(CART_FAILS_FOR_USER, 1L, 1)));

        verify(repo, times(1)).save(any(Order.class));
    }

    // ---- order history (GET /orders/user/{userId}) ----

    @Test
    void orderHistoryReturnsUsersOrders() throws Exception {
        Order o = new Order();
        o.setId(5L);
        o.setUserId(7L);
        o.setStatus("PAID");
        o.setTotalPrice(new BigDecimal("999.99"));
        when(repo.findByUserId(7L)).thenReturn(List.of(o));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(Duration.ofSeconds(5))).build();
        mvc.perform(get("/orders/user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(7))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].totalPrice").value(999.99));
    }

    @Test
    void orderHistoryForUserWithoutOrdersIsEmptyArray() throws Exception {
        when(repo.findByUserId(8L)).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(Duration.ofSeconds(5))).build();
        mvc.perform(get("/orders/user/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
