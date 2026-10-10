package com.ecommerce.order.service.impl;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.exception.CartNotFoundException;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.apiResponse.PageResponseDto;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.order.dto.response.OrderResponseDto;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.shared.enums.OrderStatus;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.shared.enums.Role;
import com.ecommerce.shared.enums.PaymentStatus;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.common.baseEntity.Money;
import com.ecommerce.common.utils.SecurityUtils;
import com.ecommerce.notification.NotificationService;

import com.ecommerce.inventory.dto.request.UpdateStockRequestDto;
import com.ecommerce.payment.dto.request.PaymentCreateRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional   // 🔥 VERY IMPORTANT
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final OrderMapper orderMapper;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;


    @Override
    @Transactional
    public OrderResponseDto placeOrder(Long userId) {
        log.info("placeOrder started for userId={}", userId);

        // 1️⃣ Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2️⃣ Fetch cart
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        if (cart.getItems().isEmpty()) {
            log.warn("placeOrder empty cart for userId={}", userId);
            throw new RuntimeException("Cannot place order with empty cart");
        }

        // 3️⃣ Create Order
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);

        BigDecimal total = BigDecimal.ZERO;

        // 4️⃣ Convert cart items → order items + REDUCE INVENTORY
        for (CartItem cartItem : cart.getItems()) {

            // 🔥 THIS WAS MISSING
            UpdateStockRequestDto stockRequest =
                new UpdateStockRequestDto();
            stockRequest.setQuantity(cartItem.getQuantity());
            inventoryService.reduceStock(cartItem.getProduct().getId(), stockRequest);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());

            order.getItems().add(orderItem);

            total = total.add(
                    cartItem.getPrice().getAmount()
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
            );
        }

        // 5️⃣ Set total amount
        order.setTotalAmount(new Money(total, "INR"));

        // 6️⃣ Save order
        Order savedOrder = orderRepository.save(order);
        log.info("placeOrder completed orderId={} userId={}", savedOrder.getId(), userId);

        // Create payment entry (COD for now) - default behavior for backward compatibility
        PaymentCreateRequestDto paymentRequest =
            new PaymentCreateRequestDto();
        paymentRequest.setOrderId(savedOrder.getId());
        paymentRequest.setPaymentMethod(PaymentMethod.COD);
        paymentService.createPayment(paymentRequest);

        // 7️⃣ Clear cart SAFELY
        cart.getItems().clear();
        cartRepository.save(cart);

        // Note: Email will be sent when seller/admin confirms the order, not on creation
        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId)
                .stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponseDto> getOrdersByUser(Long userId, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByUserId(userId, pageable);
        List<OrderResponseDto> content = orderPage.getContent()
                .stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());

        return PageResponseDto.<OrderResponseDto>builder()
                .content(content)
                .pageNumber(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .empty(orderPage.isEmpty())
                .build();
    }

    @Override
    public OrderResponseDto shipOrder(Long orderId) {
        log.info("shipOrder started for orderId={}", orderId);
        Order order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("shipOrder invalid status orderId={} status={}", orderId, order.getStatus());
            throw new IllegalStateException("Only CONFIRMED orders can be shipped");
        }

        order.setStatus(OrderStatus.SHIPPED);
        Order savedOrder = orderRepository.save(order);
        log.info("shipOrder completed orderId={}", orderId);

        // Send order shipped notification (async side effect)
        notificationService.sendOrderShippedNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName(), "TRACKING123"); // TODO: Generate actual tracking number

        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDto deliverOrder(Long orderId) {
        log.info("deliverOrder started for orderId={}", orderId);
        Order order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.SHIPPED) {
            log.warn("deliverOrder invalid status orderId={} status={}", orderId, order.getStatus());
            throw new IllegalStateException("Only SHIPPED orders can be delivered");
        }

        order.setStatus(OrderStatus.DELIVERED);
        Order savedOrder = orderRepository.save(order);
        log.info("deliverOrder completed orderId={}", orderId);

        // Send order delivered notification (async side effect)
        notificationService.sendOrderDeliveredNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName());

        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDto cancelOrder(Long orderId) {
        log.info("cancelOrder started for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        switch (order.getStatus()) {

            case CREATED:
                // No payment → no refund
                order.setStatus(OrderStatus.CANCELLED);
                log.info("cancelOrder CREATED->CANCELLED orderId={}", orderId);
                break;

            case PAID:
            case SHIPPED:
                // Payment exists → refund required
                log.info("cancelOrder processing refund orderId={}", orderId);
                paymentService.refundPayment(order.getId());
                order.setStatus(OrderStatus.REFUNDED);
                break;

            case DELIVERED:
                log.warn("cancelOrder cannot cancel delivered order orderId={}", orderId);
                throw new IllegalStateException("Delivered order cannot be cancelled");

            default:
                log.error("cancelOrder invalid order state orderId={} status={}", orderId, order.getStatus());
                throw new IllegalStateException("Invalid order state");
        }

        Order savedOrder = orderRepository.save(order);
        log.info("cancelOrder completed orderId={}", orderId);
        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDto confirmPayment(Long orderId) {
        log.info("confirmPayment started for orderId={}", orderId);
        Order order = getOrderOrThrow(orderId);

        try {
            // ⚠️ IMPORTANT: Update PAYMENT status, NOT order status!
            // Payment confirmation should work regardless of order status
            // The payment status drives the ability to confirm (must be PENDING)
            var payment = paymentService.confirmPaymentByAdmin(orderId);
            log.info("confirmPayment completed orderId={} paymentStatus={}", orderId, payment.getStatus());

            // Note: Email will be sent when seller confirms the order, not on payment confirmation
            return orderMapper.toDto(order);
        } catch (Exception e) {
            log.error("Error confirming payment for orderId={}", orderId, e);
            throw new RuntimeException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public OrderResponseDto confirmOrderAsAdmin(Long orderId) {
        log.info("confirmOrderAsAdmin started for orderId={}", orderId);
        Order order = getOrderOrThrow(orderId);

        // Validate order is in PAID or CREATED state
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.CREATED) {
            log.warn("confirmOrderAsAdmin invalid status orderId={} status={}", orderId, order.getStatus());
            throw new IllegalStateException("Only PAID or CREATED orders can be confirmed. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        log.info("confirmOrderAsAdmin completed orderId={} by admin", orderId);

        // Send notification to customer (async side effect)
        log.info("[OrderService] About to call sendOrderConfirmedNotification from admin - orderId={}, email={}, name={}", 
                savedOrder.getId(), order.getUser().getEmail(), order.getUser().getFullName());
        notificationService.sendOrderConfirmedNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName());
        log.info("[OrderService] sendOrderConfirmedNotification called (async method)");

        return orderMapper.toDto(savedOrder);
    }

    @Override
    public OrderResponseDto placeOrderForCurrentUser() {
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return placeOrder(userId);
    }

    @Override
    @Transactional
    public OrderResponseDto placeOrder(Long userId, PaymentMethod paymentMethod) {
        log.info("placeOrder started for userId={} paymentMethod={}", userId, paymentMethod);

        // 1️⃣ Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2️⃣ Fetch cart
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        if (cart.getItems().isEmpty()) {
            log.warn("placeOrder empty cart for userId={}", userId);
            throw new RuntimeException("Cannot place order with empty cart");
        }

        // 3️⃣ Create Order
        Order order = new Order();
        order.setUser(user);
        // For ONLINE payment: order is created with PAID status (payment already verified in frontend)
        // For COD: order is created with CREATED status
        if (paymentMethod == PaymentMethod.ONLINE) {
            order.setStatus(OrderStatus.PAID);
            log.info("ONLINE payment: Creating order with PAID status for userId={}", userId);
        } else {
            order.setStatus(OrderStatus.CREATED);
            log.info("COD payment: Creating order with CREATED status for userId={}", userId);
        }

        BigDecimal total = BigDecimal.ZERO;

        // 4️⃣ Convert cart items → order items + REDUCE INVENTORY
        for (CartItem cartItem : cart.getItems()) {
            UpdateStockRequestDto stockRequest =
                new UpdateStockRequestDto();
            stockRequest.setQuantity(cartItem.getQuantity());
            inventoryService.reduceStock(cartItem.getProduct().getId(), stockRequest);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());

            order.getItems().add(orderItem);

            total = total.add(
                    cartItem.getPrice().getAmount()
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
            );
        }

        // 5️⃣ Set total amount
        order.setTotalAmount(new Money(total, "INR"));

        // 6️⃣ Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with ID: {}", savedOrder != null ? savedOrder.getId() : "NULL");
        
        if (savedOrder == null || savedOrder.getId() == null) {
            log.error("CRITICAL: Order was not saved correctly! savedOrder={}", savedOrder != null ? "exists but no ID" : "is NULL");
            throw new RuntimeException("Failed to save order - order ID is null");
        }
        log.info("placeOrder completed orderId={} userId={}, paymentMethod={}", savedOrder.getId(), userId, paymentMethod);

        // 7️⃣ Create payment entry based on payment method
        Long orderId = savedOrder.getId();
        log.info("About to create payment with orderId={}", orderId);
        
        PaymentCreateRequestDto paymentRequest =
            new PaymentCreateRequestDto();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setPaymentMethod(paymentMethod);
        
        log.info("Payment DTO created - orderId={}, paymentMethod={}", paymentRequest.getOrderId(), paymentRequest.getPaymentMethod());
        try {
            paymentService.createPayment(paymentRequest);
        } catch (Exception e) {
            log.error("Failed to create payment for orderId={}: {}", savedOrder.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
        
        // For ONLINE payments: immediately mark as SUCCESS (signature already verified in frontend)
        if (paymentMethod == PaymentMethod.ONLINE) {
            Payment payment = paymentRepository.findByOrderId(savedOrder.getId())
                    .orElseThrow(() -> new RuntimeException("Payment record not found after creation"));
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            log.info("ONLINE payment marked as SUCCESS for orderId={}", savedOrder.getId());
            
            // Send payment received notification for ONLINE orders
            log.info("[OrderService] Sending order confirmation email for ONLINE payment - orderId={}", savedOrder.getId());
            notificationService.sendOrderConfirmation(savedOrder.getId(), user.getEmail(), user.getFullName());
        }

        // 8️⃣ Clear cart SAFELY
        cart.getItems().clear();
        cartRepository.save(cart);

        // Note: Email will be sent when seller/admin confirms the order, not on creation
        return orderMapper.toDto(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponseDto placeOrder(Long userId, PaymentMethod paymentMethod, List<Long> selectedProductIds) {
        log.info("placeOrder started for userId={} paymentMethod={} selectedProductIds={}", userId, paymentMethod, selectedProductIds);

        // Validate selectedProductIds is not null or empty
        if (selectedProductIds == null || selectedProductIds.isEmpty()) {
            log.warn("placeOrder selectedProductIds is null or empty, treating as empty list");
            selectedProductIds = new java.util.ArrayList<>();
        }

        // 1️⃣ Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2️⃣ Fetch cart
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        if (cart.getItems().isEmpty()) {
            log.warn("placeOrder empty cart for userId={}", userId);
            throw new RuntimeException("Cannot place order with empty cart");
        }

        // 3️⃣ Create Order
        Order order = new Order();
        order.setUser(user);
        // For ONLINE payment: order is created with PAID status (payment already verified in frontend)
        // For COD: order is created with CREATED status
        if (paymentMethod == PaymentMethod.ONLINE) {
            order.setStatus(OrderStatus.PAID);
            log.info("ONLINE payment: Creating order with PAID status for userId={}", userId);
        } else {
            order.setStatus(OrderStatus.CREATED);
            log.info("COD payment: Creating order with CREATED status for userId={}", userId);
        }

        BigDecimal total = BigDecimal.ZERO;

        // 4️⃣ Convert selected cart items → order items + REDUCE INVENTORY
        List<CartItem> itemsToRemove = new java.util.ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            // Only include this item if it's in the selected product IDs
            // If selectedProductIds is empty, include all items (fallback to all items)
            if (!selectedProductIds.isEmpty() && !selectedProductIds.contains(cartItem.getProduct().getId())) {
                log.debug("Skipping product {} as it's not in selected products", cartItem.getProduct().getId());
                continue;
            }

            UpdateStockRequestDto stockRequest =
                new UpdateStockRequestDto();
            stockRequest.setQuantity(cartItem.getQuantity());
            inventoryService.reduceStock(cartItem.getProduct().getId(), stockRequest);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());

            order.getItems().add(orderItem);

            total = total.add(
                    cartItem.getPrice().getAmount()
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity()))
            );
            
            // Mark this item for removal from cart
            itemsToRemove.add(cartItem);
        }

        if (order.getItems().isEmpty()) {
            log.warn("placeOrder no valid items found for selected product IDs={}", selectedProductIds);
            throw new RuntimeException("No valid products found for the selected items");
        }

        // 5️⃣ Set total amount
        order.setTotalAmount(new Money(total, "INR"));

        // 6️⃣ Persist Order (with payment pending)
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with ID: {}", savedOrder != null ? savedOrder.getId() : "NULL");
        
        if (savedOrder == null || savedOrder.getId() == null) {
            log.error("CRITICAL: Order was not saved correctly! savedOrder={}", savedOrder != null ? "exists but no ID" : "is NULL");
            throw new RuntimeException("Failed to save order - order ID is null");
        }
        log.info("placeOrder persisted orderId={} with {} items, paymentMethod={}", savedOrder.getId(), order.getItems().size(), paymentMethod);

        // 7️⃣ Create Payment 
        Long orderId = savedOrder.getId();
        log.info("About to create payment with orderId={}", orderId);
        
        PaymentCreateRequestDto paymentRequest = new PaymentCreateRequestDto();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setPaymentMethod(paymentMethod);
        
        log.info("Payment DTO created - orderId={}, paymentMethod={}", paymentRequest.getOrderId(), paymentRequest.getPaymentMethod());
        try {
            paymentService.createPayment(paymentRequest);
        } catch (Exception e) {
            log.error("Failed to create payment for orderId={}: {}", savedOrder.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
        
        // For ONLINE payments: immediately mark as SUCCESS (signature already verified in frontend)
        if (paymentMethod == PaymentMethod.ONLINE) {
            Payment payment = paymentRepository.findByOrderId(savedOrder.getId())
                    .orElseThrow(() -> new RuntimeException("Payment record not found after creation"));
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            log.info("ONLINE payment marked as SUCCESS for orderId={}", savedOrder.getId());
            
            // Send payment received notification for ONLINE orders
            log.info("[OrderService] Sending order confirmation email for ONLINE payment - orderId={}", savedOrder.getId());
            notificationService.sendOrderConfirmation(savedOrder.getId(), user.getEmail(), user.getFullName());
        }

        // 8️⃣ Remove only the selected items from cart
        cart.getItems().removeAll(itemsToRemove);
        cartRepository.save(cart);
        log.info("Removed {} items from cart", itemsToRemove.size());

        // Note: Email will be sent when seller/admin confirms the order, not on creation
        return orderMapper.toDto(savedOrder);
    }

    @Override
    public OrderResponseDto placeOrderForCurrentUser(PaymentMethod paymentMethod) {
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return placeOrder(userId, paymentMethod);
    }

    @Override
    public OrderResponseDto placeOrderForCurrentUser(PaymentMethod paymentMethod, List<Long> selectedProductIds) {
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return placeOrder(userId, paymentMethod, selectedProductIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersForCurrentUser() {
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return getOrdersByUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponseDto> getOrdersForCurrentUser(Pageable pageable) {
        Long userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return getOrdersByUser(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        
        // Check ownership: Admin can see all orders, sellers can see orders with their items, users can see their own
        String currentUsername = securityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        
        User currentUser = userRepository.findByEmail(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        
        Long currentUserId = securityUtils.getCurrentUserId();
        
        // Admin can access any order
        if (currentUser.getRoles().contains(Role.ROLE_ADMIN)) {
            return orderMapper.toDto(order);
        }
        
        // Seller can access orders that contain their products
        if (currentUser.getRoles().contains(Role.ROLE_SELLER)) {
            // Check if this order contains any items from this seller's products
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                boolean sellerHasItems = order.getItems().stream()
                        .anyMatch(item -> item.getProduct() != null && 
                                 item.getProduct().getSeller() != null && 
                                 item.getProduct().getSeller().getId().equals(currentUserId));
                if (sellerHasItems) {
                    return orderMapper.toDto(order);
                }
            }
            log.warn("Access denied: Seller {} attempted to access order {} with no items from them", 
                    currentUserId, orderId);
            throw new AccessDeniedException("You do not have permission to access this order");
        }
        
        // Regular users can only access their own orders
        if (order.getUser() == null || !order.getUser().getId().equals(currentUserId)) {
            log.warn("Access denied: User {} attempted to access order {} owned by user {}", 
                    currentUserId, orderId, order.getUser() != null ? order.getUser().getId() : "null");
            throw new AccessDeniedException("You do not have permission to access this order");
        }
        
        return orderMapper.toDto(order);
    }

    @Override
    public List<OrderResponseDto> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponseDto> getAllOrders(Pageable pageable) {
        Page<Order> orderPage = orderRepository.findAll(pageable);
        List<OrderResponseDto> content = orderPage.getContent()
                .stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());

        return PageResponseDto.<OrderResponseDto>builder()
                .content(content)
                .pageNumber(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .empty(orderPage.isEmpty())
                .build();
    }

    // ========================= PRIVATE HELPER =========================
    private Order getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    // ========================= COUNT OPERATIONS =========================
    @Override
    public long getTotalOrderCount() {
        return orderRepository.count();
    }

    @Override
    public long getPendingOrderCount() {
        return orderRepository.countByStatus(OrderStatus.CREATED);
    }

    // ========================= SELLER OPERATIONS =========================
    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersForSeller(Long sellerId) {
        log.info("getOrdersForSeller started for sellerId={}", sellerId);
        List<Order> orders = orderRepository.findOrdersContainingSeller(sellerId);
        return orders.stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponseDto> getOrdersForSeller(Long sellerId, Pageable pageable) {
        log.info("getOrdersForSeller (paginated) started for sellerId={}", sellerId);
        Page<Order> orderPage = orderRepository.findOrdersContainingSeller(sellerId, pageable);
        return mapToPageResponseDto(orderPage);
    }

    /**
     * Helper method to convert Page<Order> to PageResponseDto
     */
    private PageResponseDto<OrderResponseDto> mapToPageResponseDto(Page<Order> orderPage) {
        PageResponseDto<OrderResponseDto> pageResponseDto = new PageResponseDto<>();
        pageResponseDto.setContent(orderPage.getContent().stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList()));
        pageResponseDto.setPageNumber(orderPage.getNumber());
        pageResponseDto.setPageSize(orderPage.getSize());
        pageResponseDto.setTotalElements(orderPage.getTotalElements());
        pageResponseDto.setTotalPages(orderPage.getTotalPages());
        pageResponseDto.setFirst(orderPage.isFirst());
        pageResponseDto.setLast(orderPage.isLast());
        pageResponseDto.setEmpty(orderPage.isEmpty());
        return pageResponseDto;
    }

    // ========================= SELLER ORDER MANAGEMENT =========================
    /**
     * Seller confirms/accepts an order (transitions PAID -> CONFIRMED)
     * Only seller who owns products in the order can confirm
     */
    @Override
    @Transactional
    public OrderResponseDto confirmOrder(Long orderId, Long sellerId) {
        log.info("confirmOrder started for orderId={}, sellerId={}", orderId, sellerId);
        Order order = getOrderOrThrow(orderId);

        // Validate seller owns products in this order
        if (!isSellerAuthorizedForOrder(order, sellerId)) {
            log.warn("Seller {} attempted to confirm order {} without authorization", sellerId, orderId);
            throw new AccessDeniedException("You are not authorized to confirm this order");
        }

        // Validate order is in correct state for confirmation
        // For COD: Can confirm from CREATED or PAID status
        // For ONLINE: Can only confirm from PAID status
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment != null && payment.getPaymentMethod() == PaymentMethod.ONLINE) {
            // ONLINE orders must be PAID first
            if (order.getStatus() != OrderStatus.PAID) {
                log.warn("confirmOrder invalid status for ONLINE orderId={} status={}", orderId, order.getStatus());
                throw new IllegalStateException("Only PAID orders can be confirmed. Current status: " + order.getStatus());
            }
        } else {
            // COD orders can be confirmed from CREATED or PAID
            if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAID) {
                log.warn("confirmOrder invalid status orderId={} status={}", orderId, order.getStatus());
                throw new IllegalStateException("Order must be in CREATED or PAID status to confirm. Current status: " + order.getStatus());
            }
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        log.info("confirmOrder completed orderId={} by sellerId={}", orderId, sellerId);

        // Send notification to customer (async side effect)
        log.info("[OrderService] About to call sendOrderConfirmedNotification - orderId={}, email={}, name={}", 
                savedOrder.getId(), order.getUser().getEmail(), order.getUser().getFullName());
        notificationService.sendOrderConfirmedNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName());
        log.info("[OrderService] sendOrderConfirmedNotification called (async method)");

        return orderMapper.toDto(savedOrder);
    }

    /**
     * Seller ships order (transitions CONFIRMED -> SHIPPED)
     * Only seller who owns products in the order can ship
     */
    @Override
    @Transactional
    public OrderResponseDto shipOrderBySeller(Long orderId, Long sellerId) {
        log.info("shipOrderBySeller started for orderId={}, sellerId={}", orderId, sellerId);
        Order order = getOrderOrThrow(orderId);

        // Validate seller owns products in this order
        if (!isSellerAuthorizedForOrder(order, sellerId)) {
            log.warn("Seller {} attempted to ship order {} without authorization", sellerId, orderId);
            throw new AccessDeniedException("You are not authorized to ship this order");
        }

        // Validate order is in CONFIRMED state
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("shipOrderBySeller invalid status orderId={} status={}", orderId, order.getStatus());
            throw new IllegalStateException("Only CONFIRMED orders can be shipped. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.SHIPPED);
        Order savedOrder = orderRepository.save(order);
        log.info("shipOrderBySeller completed orderId={} by sellerId={}", orderId, sellerId);

        // Send notification (async side effect)
        notificationService.sendOrderShippedNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName(), "TRACKING123");

        return orderMapper.toDto(savedOrder);
    }

    /**
     * Seller marks order as delivered (transitions SHIPPED -> DELIVERED)
     * Only seller who owns products in the order can deliver
     */
    @Override
    @Transactional
    public OrderResponseDto deliverOrderBySeller(Long orderId, Long sellerId) {
        log.info("deliverOrderBySeller started for orderId={}, sellerId={}", orderId, sellerId);
        Order order = getOrderOrThrow(orderId);

        // Validate seller owns products in this order
        if (!isSellerAuthorizedForOrder(order, sellerId)) {
            log.warn("Seller {} attempted to deliver order {} without authorization", sellerId, orderId);
            throw new AccessDeniedException("You are not authorized to deliver this order");
        }

        // Validate order is in SHIPPED state
        if (order.getStatus() != OrderStatus.SHIPPED) {
            log.warn("deliverOrderBySeller invalid status orderId={} status={}", orderId, order.getStatus());
            throw new IllegalStateException("Only SHIPPED orders can be delivered. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.DELIVERED);
        Order savedOrder = orderRepository.save(order);
        log.info("deliverOrderBySeller completed orderId={} by sellerId={}", orderId, sellerId);

        // Send notification (async side effect)
        notificationService.sendOrderDeliveredNotification(savedOrder.getId(), order.getUser().getEmail(),
                order.getUser().getFullName());

        return orderMapper.toDto(savedOrder);
    }

    /**
     * Check if seller is authorized to manage this order
     * Seller is authorized if they own at least one product in the order
     */
    private boolean isSellerAuthorizedForOrder(Order order, Long sellerId) {
        return order.getItems().stream()
                .anyMatch(item -> item.getProduct().getSeller() != null 
                        && item.getProduct().getSeller().getId().equals(sellerId));
    }
}