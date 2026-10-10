# Monolithic E-commerce Baseline — Architecture

## 1. System Overview


This document describes the modular-monolith e-commerce backend used as the baseline for our research comparing monolithic and microservices architectures. It summarizes the application's domain modules, dependencies, persistence layer,
API structure, and deployment model.


```
Phase 1: Domain Entities → Phase 2: CRUD Operations → Phase 3: API Design → Phase 4: Cross-Cutting Concerns
```

### Architecture Pattern
- **Type:** Modular Monolithic (single deployable JAR, multiple independent modules)
- **Design Paradigm:** Domain-Driven Design (DDD)
- **Development Approach:** Incremental (build module by module)
- **Deployment:** Containerized (Docker) with environment-based configuration

---

## 2. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                               │
│  Web Browsers → HTML/REST API Calls / Mobile → REST API Calls  │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                   API GATEWAY LAYER                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Filters & Interceptors                                  │  │
│  │ • CORS Filter                                           │  │
│  │ • JwtAuthenticationFilter (validates JWT tokens)        │  │
│  │ • SecurityContextFilter (sets user context)             │  │
│  │ • Rate Limiting Filter (Bucket4j)                       │  │
│  │ • Error Handling (GlobalExceptionHandler)               │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│              PRESENTATION LAYER (Controllers)                   │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐     │
│  │ @RestController│ │ @RestController│ │ @Controller    │     │
│  │  /api/v1/*     │ │  /api/v1/*     │ │ /admin, /user  │     │
│  │  (JSON APIs)   │ │  (JSON APIs)   │ │ (HTML Pages)   │     │
│  │                │ │                │ │ (Thymeleaf)    │     │
│  │ AuthController │ │ProductControler│ │AdminController │     │
│  │ OrderControler │ │CartController  │ │SellerController│     │
│  │PaymentControler│ │UserController  │ │   UI Views     │     │
│  └────────────────┘ └────────────────┘ └────────────────┘     │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│            SECURITY & AUTHENTICATION LAYER                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Spring Security Configuration                           │  │
│  │ • JWT Token Validation (HS512 algorithm)                │  │
│  │ • OAuth2 Client Flow (Google Sign-In)                  │  │
│  │ • Role-Based Access Control (RBAC)                     │  │
│  │ • BCrypt Password Encoding (strength: 12)               │  │
│  │ • @PreAuthorize & @Secured annotations                 │  │
│  │ • User principal extraction & context management        │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│            BUSINESS LOGIC LAYER (Services)                      │
│                                                                 │
│  Domain 1: User Management          Domain 2: Products         │
│  ├── UserService                    ├── ProductService         │
│  ├── AuthService                    ├── CategoryService        │
│  ├── SellerService                  └── BrandService           │
│  └── RefreshTokenService                                       │
│                                                                 │
│  Domain 3: Shopping                 Domain 4: Order Processing │
│  ├── CartService                    ├── OrderService           │
│  └── CartItemService                ├── OrderItemService       │
│                                     └── InventoryService       │
│                                                                 │
│  Domain 5: Payments                 Domain 6: Notifications    │
│  ├── PaymentService                 └── EmailService           │
│  └── RazorpayGateway                   (async via events)      │
│                                                                 │
│  Cross-Cutting Services:                                       │
│  ├── CacheService (Spring Cache)                               │
│  ├── FileUploadService (Cloudinary)                            │
│  └── SecurityService (user context)                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│           DATA TRANSFER LAYER (DTOs & Mappers)                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Request DTOs    ──MapStruct--> Entities                 │  │
│  │ Response DTOs   <--MapStruct-- Entities                 │  │
│  │ @Valid annotations for input validation                 │  │
│  │ Custom validators for business rules                    │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│      PERSISTENCE LAYER (JPA/Repositories)                       │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐     │
│  │ UserRepository │ │ProductRepository│ │OrderRepository │     │
│  │ • findByEmail()│ │ • findByName() │ │ • findByUserId()     │
│  │ • Custom @Query│ │ • Pagination   │ │ • Filtering     │     │
│  └────────────────┘ └────────────────┘ └────────────────┘     │
│                                                                 │
│  • Spring Data JPA with Hibernate ORM                          │
│  • JPQL Queries, Native SQL (parameterized)                    │
│  • Pagination & Sorting Support                                │
│  • Lazy & Eager Loading Strategies                             │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│           DATABASE LAYER                                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Primary: MySQL 8.0                                      │  │
│  │ Test: H2 Database (in-memory)                           │  │
│  │ Migrations: Flyway (Version Control for Schema)         │  │
│  │ • V1__initial_schema.sql                                │  │
│  │ • V2__create_roles_and_user_roles.sql                   │  │
│  │ • V3__add_business_features.sql                         │  │
│  │ 15+ tables with proper indexing & constraints           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Module Structure & Dependencies

### Module Organization

```
ecommerce (Root Module)
│
├── 📦 common/                          [FOUNDATION]
│   ├── baseEntity/BaseEntity.java      (All entities extend this)
│   ├── apiResponse/ApiResponse.java    (Unified response format)
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java (Catch all exceptions)
│   │   ├── ResourceNotFoundException.java
│   │   ├── InvalidCredentialsException.java
│   │   └── BusinessLogicException.java
│   └── utils/
│       ├── SecurityUtils.java          (Get current user)
│       ├── ValidationUtils.java
│       └── DateUtils.java
│
├── 📦 shared/                          [ENUMS & CONSTANTS]
│   ├── enums/UserRole.java             (ADMIN, SELLER, USER)
│   ├── enums/UserStatus.java           (ACTIVE, INACTIVE, BANNED)
│   ├── enums/OrderStatus.java          (PENDING, CONFIRMED, etc.)
│   ├── enums/PaymentStatus.java        (PENDING, SUCCESS, FAILED)
│   ├── enums/InventoryStatus.java
│   └── constants/AppConstants.java
│
├── 📦 auth/                            [AUTHENTICATION]
│   ├── entity/
│   │   └── RefreshToken.java
│   ├── service/
│   │   ├── AuthService.java
│   │   └── RefreshTokenService.java
│   ├── controller/AuthController.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── AuthResponse.java
│   │   └── RefreshTokenRequest.java
│   ├── repository/
│   │   └── RefreshTokenRepository.java
│   └── exception/InvalidTokenException.java
│
├── 📦 security/                        [SPRING SECURITY CONFIG]
│   ├── config/
│   │   ├── SecurityConfig.java         (Main security configuration)
│   │   └── CorsConfig.java
│   ├── jwt/
│   │   ├── JwtTokenProvider.java       (Generate & validate JWT)
│   │   ├── JwtAuthenticationFilter.java (Extract JWT from request)
│   │   └── JwtProperties.java
│   ├── oauth2/
│   │   ├── OAuth2SuccessHandler.java   (Handle OAuth2 success)
│   │   ├── OAuth2FailureHandler.java
│   │   └── OAuth2UserService.java
│   ├── authentication/
│   │   └── FormLoginSuccessHandler.java
│   ├── filter/
│   │   └── SellerRoleRefreshFilter.java
│   ├── logout/
│   │   └── CustomLogoutSuccessHandler.java
│   └── exception/
│       ├── RestAuthenticationEntryPoint.java
│       └── RestAccessDeniedHandler.java
│
├── 📦 user/                            [USER MANAGEMENT]
│   ├── entity/
│   │   ├── User.java                   (Main user entity)
│   │   └── SellerApplication.java      (Seller signup requests)
│   ├── service/
│   │   ├── UserService.java            (CRUD operations)
│   │   ├── UserDetailsService.java     (Spring Security integration)
│   │   └── SellerService.java          (Seller-specific logic)
│   ├── controller/UserController.java  (REST endpoints)
│   ├── dto/
│   │   ├── UserRequest.java
│   │   ├── UserResponse.java
│   │   ├── SellerApplicationDto.java
│   │   └── UserProfileUpdate.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── SellerApplicationRepository.java
│   └── exception/UserNotFoundException.java
│
├── 📦 product/                         [PRODUCT CATALOG]
│   ├── entity/
│   │   ├── Product.java                (Main product entity)
│   │   ├── Category.java
│   │   ├── Brand.java
│   │   ├── ProductImage.java
│   │   └── ProductReview.java
│   ├── service/
│   │   ├── ProductService.java         (CRUD + search)
│   │   ├── CategoryService.java
│   │   ├── BrandService.java
│   │   └── ProductImageService.java
│   ├── controller/
│   │   ├── ProductController.java      (/api/v1/products)
│   │   ├── CategoryController.java
│   │   └── BrandController.java
│   ├── dto/
│   │   ├── ProductRequest.java
│   │   ├── ProductResponse.java
│   │   ├── ProductSearchCriteria.java
│   │   └── CategoryDto.java
│   ├── repository/
│   │   ├── ProductRepository.java      (Custom @Query methods)
│   │   ├── CategoryRepository.java
│   │   └── BrandRepository.java
│   └── mapper/ProductMapper.java       (MapStruct mapper)
│
├── 📦 cart/                            [SHOPPING CART]
│   ├── entity/
│   │   ├── Cart.java
│   │   └── CartItem.java
│   ├── service/
│   │   ├── CartService.java
│   │   └── CartItemService.java
│   ├── controller/CartController.java  (/api/v1/cart)
│   ├── dto/
│   │   ├── AddToCartRequest.java
│   │   ├── CartResponse.java
│   │   └── CartItemDto.java
│   ├── repository/
│   │   ├── CartRepository.java
│   │   └── CartItemRepository.java
│   └── exception/CartNotFoundException.java
│
├── 📦 inventory/                       [STOCK MANAGEMENT]
│   ├── entity/
│   │   └── Inventory.java              (Stock quantities)
│   ├── service/
│   │   └── InventoryService.java       (Reserve, release stock)
│   ├── repository/
│   │   └── InventoryRepository.java
│   ├── dto/
│   │   └── InventoryDto.java
│   └── exception/InsufficientStockException.java
│
├── 📦 order/                           [ORDER MANAGEMENT]
│   ├── entity/
│   │   ├── Order.java                  (Main order entity)
│   │   └── OrderItem.java              (Line items)
│   ├── service/
│   │   ├── OrderService.java           (CRUD + validation)
│   │   │   └── impl/OrderServiceImpl.java
│   │   └── OrderItemService.java
│   ├── controller/
│   │   ├── OrderController.java        (/api/v1/orders)
│   │   └── impl/OrderControllerImpl.java
│   ├── dto/
│   │   ├── CreateOrderRequest.java
│   │   ├── OrderResponse.java
│   │   └── OrderItemDto.java
│   ├── repository/
│   │   ├── OrderRepository.java
│   │   └── OrderItemRepository.java
│   ├── mapper/OrderMapper.java
│   └── exception/
│       ├── OrderNotFoundException.java
│       └── OrderProcessingException.java
│
├── 📦 payment/                         [RAZORPAY INTEGRATION]
│   ├── gateway/
│   │   └── RazorpayGateway.java        (Razorpay API wrapper)
│   ├── service/
│   │   ├── PaymentService.java         (Payment orchestration)
│   │   └── PaymentVerificationService.java
│   ├── controller/PaymentController.java (/api/v1/payments)
│   ├── dto/
│   │   ├── CreatePaymentRequest.java
│   │   ├── VerifyPaymentRequest.java
│   │   ├── PaymentResponse.java
│   │   └── RazorpayOrderResponse.java
│   ├── entity/
│   │   └── Payment.java                (Payment transaction records)
│   ├── repository/
│   │   └── PaymentRepository.java
│   └── exception/PaymentException.java
│
├── 📦 notification/                    [EMAIL NOTIFICATIONS]
│   ├── service/
│   │   ├── EmailService.java           (Send email async)
│   │   └── NotificationService.java
│   ├── event/
│   │   ├── OrderPlacedEvent.java       (Domain event)
│   │   ├── PaymentSuccessEvent.java
│   │   └── NotificationListener.java   (@EventListener)
│   ├── config/
│   │   └── EmailConfig.java            (SMTP configuration)
│   ├── dto/
│   │   └── EmailRequest.java
│   └── template/
│       ├── order-confirmation.html
│       ├── payment-receipt.html
│       └── seller-notification.html
│
├── 📦 config/                          [APPLICATION CONFIG]
│   ├── AdminProperties.java            (Admin configuration properties)
│   ├── AdminBootstrap.java             (Create default admin on startup)
│   ├── AppConfig.java                  (General beans)
│   ├── AuditingConfig.java             (JPA auditing)
│   ├── CacheConfig.java                (Spring Cache configuration)
│   └── WebConfig.java                  (Web-related beans)
│
└── 📦 ui/                              [THYMELEAF VIEWS]
    ├── controller/
    │   ├── HomeController.java         (Public pages)
    │   ├── AdminController.java        (/admin/*)
    │   ├── SellerController.java       (/seller/*)
    │   └── UserPageController.java     (/user/*)
    └── (Views in resources/templates/)
        ├── index.html
        ├── admin/
        ├── seller/
        └── user/
```

---

## 4. Development Phases (Incremental Approach)

### Phase 1: Domain Entities
Create entity classes with relationships:
```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(unique = true, nullable = false)
    private String email;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Order> orders;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles")
    private Set<Role> roles;
    // ...
}
```

**Entities created:**
- User (with roles)
- Product, Category, Brand, ProductImage
- Order, OrderItem
- Cart, CartItem
- Inventory
- Payment
- RefreshToken
- SellerApplication
- Notification records

### Phase 2: CRUD Operations
Implement repositories and services:
```text
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
    @Query("SELECT u FROM User u WHERE u.status = :status")
    Page<User> findActive(@Param("status") UserStatus status, Pageable page);
}

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    
    public User createUser(UserRequest request) { ... }
    public User updateUser(Long id, UserRequest request) { ... }
    public void deleteUser(Long id) { ... }
    public User getUserById(Long id) { ... }
    public Page<User> getAllUsers(Pageable page) { ... }
}
```

**Operations implemented:**
- CRUD for all entities
- Pagination and filtering
- Searching with @Query
- Custom business logic

### Phase 3: API Design
Create REST endpoints with proper HTTP methods:
```text
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductMapper mapper;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) { ... }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) { ... }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest req) { ... }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest req
    ) { ... }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { ... }
}
```

**API Design Principles:**
- **Versioning:** `/api/v1/` prefix
- **HTTP Methods:** GET (read), POST (create), PUT (update), DELETE (delete)
- **Status Codes:** 200 (OK), 201 (Created), 204 (No Content), 400 (Bad Request), 404 (Not Found), 500 (Server Error)
- **Response Format:** Unified `ApiResponse<T>` wrapper
- **Authentication:** JWT Bearer tokens in Authorization header
- **Authorization:** `@PreAuthorize` for role-based access

### Phase 4: Cross-Cutting Concerns

#### A. Spring Security & Authentication
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

#### B. Logging
```yaml
# logback-spring.xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/application.log</file>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/application-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>

<root level="INFO">
    <appender-ref ref="FILE"/>
</root>

<logger name="com.ecommerce" level="DEBUG"/>
```

#### C. Caching
```text
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products", "categories", "brands");
    }
}

@Service
public class ProductService {
    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) { ... }
    
    @CacheEvict(value = "products", key = "#id")
    public void updateProduct(Long id, ProductRequest req) { ... }
    
    @Cacheable(value = "categories")
    public List<Category> getAllCategories() { ... }
}
```

**Caching Strategy:**
- Product categories: 30-minute TTL
- Brand listings: 1-hour TTL
- User permissions: 15-minute TTL
- Implementation: `ConcurrentHashMap` (in-memory)
- **Note:** Redis not implemented; simple in-memory caching sufficient for current load

#### D. Exception Handling
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
        ResourceNotFoundException e
    ) {
        ApiResponse<Void> response = new ApiResponse<>(
            false,
            e.getMessage(),
            null,
            "RESOURCE_NOT_FOUND"
        );
        return ResponseEntity.status(NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
        MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .collect(joining(", "));
        ApiResponse<Void> response = new ApiResponse<>(false, message, null, "VALIDATION_FAILED");
        return ResponseEntity.status(BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        ApiResponse<Void> response = new ApiResponse<>(
            false,
            "Internal server error",
            null,
            "INTERNAL_ERROR"
        );
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(response);
    }
}
```

#### E. Rate Limiting
```java
@Configuration
public class RateLimitingConfig {
    @Bean
    public Bucket loginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }
}

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, 
                                    FilterChain chain) {
        if ("/api/v1/auth/login".equals(req.getRequestURI())) {
            if (!loginBucket().tryConsume(1)) {
                res.setStatus(429);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
```

---

## 5. Database Schema

### Schema Evolution with Flyway

**V1: Initial Schema** (users, products, orders, basic relationships)
**V2: Roles & Security** (roles, user_roles, refresh_tokens)
**V3: Business Features** (inventory, payments, notifications, seller features)

### Entity Relationship Diagram

```
┌─────────────┐                    ┌─────────────┐
│    User     │──────────1:N───────│    Order    │
│   (PK: id)  │                    │   (PK: id)  │
└─────────────┘                    └─────────────┘
     │                                    │
     │ 1:N                               │ 1:N
     │                                    │
     └──→ Cart                      Order Items ←── Product
     │
     └──→ RefreshToken


┌──────────────┐      1:N      ┌──────────────┐      1:N
│  Category    │◄──────────────┤   Product    │──────────────►┌────────────────┐
│  (PK: id)    │               │  (PK: id)    │               │  ProductImage  │
└──────────────┘               └──────────────┘               └────────────────┘
                                      │
                                      │ N:1
                                      │
                               ┌──────────────┐
                               │    Brand     │
                               │  (PK: id)    │
                               └──────────────┘


┌──────────────┐      N:N      ┌──────────────┐
│    User      │◄─────────────►│     Role     │
│  (PK: id)    │ user_roles    │  (PK: id)    │
└──────────────┘               └──────────────┘


┌──────────────┐      1:1      ┌──────────────┐
│   Order      │◄─────────────►│    Payment   │
│  (PK: id)    │               │  (PK: id)    │
└──────────────┘               └──────────────┘
     │
     │ 1:N
     │
┌─────────────┐              ┌─────────────┐
│Inventory    │              │    Cart     │
│(PK: id)     │              │(PK: id)     │
└─────────────┘              └─────────────┘
                                   │
                                   │ 1:N
                                   │
                             ┌──────────────┐
                             │  CartItem    │
                             │  (PK: id)    │
                             └──────────────┘
```

### Key Tables

| Table | Columns | Purpose |
|-------|---------|---------|
| `users` | id, email, password_hash, first_name, last_name, status, created_at, updated_at | User accounts & profiles |
| `roles` | id, name, description | Role definitions |
| `user_roles` | user_id, role_id | User-role assignments |
| `products` | id, name, description, price, category_id, brand_id, seller_id, status, created_at | Product catalog |
| `categories` | id, name, description, status | Product categories |
| `brands` | id, name, description, status | Product brands |
| `product_images` | id, product_id, image_url, display_order | Product images |
| `orders` | id, user_id, order_number, total_amount, status, shipping_address, created_at | Orders |
| `order_items` | id, order_id, product_id, quantity, price | Order line items |
| `payments` | id, order_id, razorpay_payment_id, amount, status, gateway_response | Payment transactions |
| `carts` | id, user_id, created_at | Shopping carts |
| `cart_items` | id, cart_id, product_id, quantity | Cart items |
| `inventory` | id, product_id, quantity_available, quantity_reserved | Stock tracking |
| `refresh_tokens` | id, user_id, token_value, expires_at | Token management |
| `seller_applications` | id, user_id, status, pan_number, gst_number | Seller signup requests |

---

## 6. API Versioning & HTTP Conventions

### URL Structure
```
/api/v1/{resource}/{id}/{sub-resource}

Examples:
GET    /api/v1/products              (list all)
GET    /api/v1/products/{id}         (get one)
POST   /api/v1/products              (create)
PUT    /api/v1/products/{id}         (update)
DELETE /api/v1/products/{id}         (delete)
GET    /api/v1/orders/{id}/items     (nested resource)
```

### HTTP Methods & Status Codes
```
GET:    200 OK, 404 Not Found, 401 Unauthorized
POST:   201 Created, 400 Bad Request, 409 Conflict
PUT:    200 OK, 204 No Content, 400 Bad Request
DELETE: 204 No Content, 404 Not Found
```

### Response Format
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": 1,
    "name": "Product Name",
    "price": 99.99
  },
  "errorCode": null
}
```

### Pagination
```
GET /api/v1/products?page=0&size=10&sort=name,asc
```

---

## 7. Design Patterns Used

### 1. Service Layer Pattern
Business logic isolated in services, repositories handle data access.

### 2. Repository Pattern
`JpaRepository` for database operations with custom queries.

### 3. DTO (Data Transfer Object)
Separate request/response DTOs from entities using MapStruct.

### 4. Mapper Pattern
MapStruct for automatic DTO ↔ Entity conversion.

### 5. Singleton Pattern
Spring beans (services, repositories) as singletons.

### 6. Dependency Injection
Constructor injection via `@RequiredArgsConstructor` (Lombok).

### 7. Strategy Pattern
Different payment gateways (Razorpay gateway wrapper).

### 8. Observer Pattern
Event-driven notifications using `@EventListener` and `ApplicationEventPublisher`.

### 9. Proxy Pattern
Spring AOP for cross-cutting concerns (caching, security).

### 10. Template Method
`BaseEntity` for common audit fields.

---

## 8. Security Architecture

### Authentication Flow
```
Client Request
    ↓
JwtAuthenticationFilter (extracts JWT from Authorization header)
    ↓
JwtTokenProvider (validates JWT signature & expiration)
    ↓
UsernamePasswordAuthenticationToken (created with authorities)
    ↓
SecurityContext (sets authenticated principal)
    ↓
Proceed to controller with authenticated user
```

### Authorization Flow
```
Controller Method Decorated with @PreAuthorize("hasRole('ADMIN')")
    ↓
Spring Security checks user roles
    ↓
If authorized: execute method
If denied: RestAccessDeniedHandler returns 403 Forbidden
```

### OAuth2 Flow (Google Sign-In)
```
Client opens Google login page
    ↓
User authenticates with Google
    ↓
Google redirects to /api/v1/auth/oauth2/callback with authorization code
    ↓
OAuth2SuccessHandler processes auth code and creates/updates user
    ↓
JWT token issued and returned
```

### Password Security
- Algorithm: **BCrypt** (strength: 12)
- Storage: Never stored in plaintext
- Transmission: Always over HTTPS in production
- Reset: Token-based with email verification

---

## 9. Event-Driven Architecture (Async Processing)

### Order Placed Event
```java
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;
    
    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        // ... set order properties
        Order savedOrder = orderRepository.save(order);
        
        // Publish event asynchronously
        eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder));
        
        return savedOrder;
    }
}

@Component
public class NotificationListener {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Send email asynchronously
        emailService.sendOrderConfirmation(event.getOrder());
    }
}
```

### Benefits
- Non-blocking order creation
- Scalable async processing
- Easy to add new listeners

---

## 10. File Upload & Storage (Cloudinary)

```java
@Service
@RequiredArgsConstructor
public class FileUploadService {
    private final Cloudinary cloudinary;
    
    public String uploadProductImage(MultipartFile file) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(
            file.getInputStream(),
            ObjectUtils.asMap("folder", "products")
        );
        return (String) uploadResult.get("secure_url");
    }
}
```

---

## 11. Database Migrations (Flyway)

### Migration Naming
```
V1__initial_schema.sql      (Create users, products, etc.)
V2__create_roles_and_user_roles.sql
V3__add_business_features.sql
```

### Migration Process
```
Application startup
    ↓
Flyway checks migration history in flyway_schema_history table
    ↓
Executes pending migrations in order
    ↓
Updates history table with migration metadata
    ↓
Application proceeds with updated schema
```

---

## 12. Configuration Management

### Application Profiles
```
application.yml             (common config)
application-dev.yml         (development: H2, debug logging)
application-test.yml        (testing: H2, minimal logging)
application-prod.yml        (production: MySQL, INFO logging)
```

### Environment Variables (Production)
```bash
SPRING_PROFILES_ACTIVE=prod
DB_HOST=database.example.com
DB_USERNAME=prod_user
DB_PASSWORD=secure_password
JWT_ACCESS_SECRET=32_character_strong_secret_key_here
JWT_REFRESH_SECRET=32_character_strong_secret_key_here
GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_secret
RAZORPAY_KEY_ID=your_key_id
RAZORPAY_KEY_SECRET=your_secret
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USER=your_email@gmail.com
MAIL_PASSWORD=your_app_password
```

---

## 13. Deployment Architecture

### Production Stack
```
Internet
    ↓
CDN / Load Balancer
    ↓
Application Instance (Docker container)
    ↓
MySQL Database
    ↓
Cloudinary (file storage)
```

### Render Deployment
- Deployed at: https://ecommerce-backend-w8mg.onrender.com
- Server: Render free tier (auto-spins down after 15 min inactivity)
- Database: PostgreSQL managed by Render
- Environment: Production profile with all credentials

### Docker Support
```dockerfile
# Multi-stage build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

---

## 14. Performance Optimization

### Database Optimization
- **Indexing:** Indexes on frequently queried columns (email, category_id)
- **Pagination:** All list endpoints paginated (reduce memory)
- **Lazy Loading:** Relationships load on-demand
- **Query Optimization:** Avoid N+1 queries with `@Fetch(FetchMode.JOIN)`

### Caching Strategy
- **Product Categories:** 30-min TTL
- **Brands:** 1-hour TTL
- **User Permissions:** 15-min TTL
- **Cache Eviction:** On create/update/delete

### Rate Limiting
- **Login endpoint:** 5 requests/minute
- **API endpoints:** 100 requests/minute (configurable)
- **Algorithm:** Token bucket (Bucket4j)

---

## 15. Testing Strategy

### Unit Tests
- Service layer testing with mocked repositories
- Validates business logic independently

### Integration Tests
- Database integration tests
- API endpoint testing
- End-to-end workflow validation

### Test Setup
```bash
mvn test                    # Run all tests
mvn test -Dtest=*Test      # Run all test files
mvn test -Dtest=OrderServiceTest  # Specific test
mvn jacoco:report           # Coverage report
```

---

## 16. Project Statistics

- **Total Modules:** 11
- **Entity Classes:** 15+
- **Repository Interfaces:** 20+
- **Service Classes:** 25+
- **Controller Classes:** 10+
- **DTO Classes:** 40+
- **Database Tables:** 15+
- **API Endpoints:** 50+
- **Test Coverage:** 80+ tests

---

## 17. Development Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1: Entities | Week 1 | All domain entities with relationships |
| Phase 2: CRUD | Week 2 | Repositories + Services |
| Phase 3: APIs | Week 3 | REST endpoints + validation |
| Phase 4: Security | Week 4 | JWT + OAuth2 + authorization |
| Phase 5: Features | Week 5-6 | Orders, payments, notifications |
| Phase 6: Polish | Week 7 | Testing, optimization, deployment |

---

## 18. Key Implementation Details

### Modular Dependency Management
- **Common:** Foundation utilities used by all modules
- **Auth:** Depends on User, Security modules
- **User:** Depends on Common, Auth modules
- **Product:** Depends on Common module
- **Order:** Depends on Product, User, Inventory, Payment, Notification
- **Payment:** Depends on Order module
- **Notification:** Depends on User, Order modules

### API Versioning Strategy
- Current version: `v1`
- Future compatibility: Easy to add `/api/v2` endpoints
- Backward compatibility: v1 remains available

### Error Handling Strategy
- Centralized `GlobalExceptionHandler`
- Meaningful error messages
- Error codes for client-side handling
- Proper HTTP status codes

---

## 19. Future Enhancement Opportunities

- [ ] Redis caching for distributed systems
- [ ] Message queue (RabbitMQ) for async processing
- [ ] Microservices migration (future)
- [ ] GraphQL API alongside REST
- [ ] Advanced analytics dashboard
- [ ] Machine learning recommendations
- [ ] Multi-currency support
- [ ] Inventory forecasting

---

## 20. Conclusion
The application provides a modular-monolith baseline for the research project.
Its domain boundaries, inter-module dependencies, database relationships, and operational characteristics will inform the design of the corresponding microservices implementation.
Performance and resource-utilization claims will be established through controlled experiments rather than assumed from the architecture documentation.
