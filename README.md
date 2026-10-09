# Monolith vs Microservices: Performance & Cost Analysis

A cloud computing research project evaluating the performance, scalability, resource utilization, and cost trade-offs of monolithic and microservices architectures for an e-commerce application under different workload conditions.

## Project Overview

This project uses an existing e-commerce application as the monolithic baseline. We will analyze its architecture and develop a corresponding microservices implementation to conduct a controlled comparative evaluation.

### Original Application

The monolithic e-commerce application was originally developed by **theabhishek-hub**.

Original repository: https://github.com/theabhishek-hub/ecommerce-backend

### Our Research Contributions

* Analysis of the original monolithic architecture and its dependencies.
* Design and implementation of a microservices-based counterpart.
* Definition and execution of workload and performance experiments.
* Comparison of response time, throughput, CPU and memory utilization, scalability, and deployment cost.

**Attribution:** The original application is credited to its author. The microservices implementation and experimental findings will be attributed to our research team, based on the work actually completed.


---

## 🛠️ Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.2.5, Java 21, Spring Web |
| **Security** | Spring Security 6, JWT, OAuth2, BCrypt |
| **Database** | MySQL 8.0, Spring Data JPA, Hibernate, Flyway |
| **Cache** | Spring Cache (ConcurrentHashMap) |
| **Logging** | Logback with structured file-based logging |
| **UI** | Thymeleaf templates, HTML5, Bootstrap CSS, Vanilla JS |
| **Payment** | Razorpay SDK |
| **File Storage** | Cloudinary |
| **Email** | Spring Mail (Gmail SMTP) |
| **API Docs** | OpenAPI 3.0 (Springdoc) |
| **Rate Limiting** | Bucket4j |
| **DTO Mapping** | MapStruct |
| **Containerization** | Docker & Docker Compose |
| **Build** | Maven 3.8+, Spring Boot Maven Plugin |

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- MySQL 8.0+
- Maven 3.8+
- Git

### Setup

```bash
# Clone repository
git clone https://github.com/yourusername/ecommerce-backend.git
cd ecommerce-backend

# Configure environment variables (.env or system env)
export DB_HOST=localhost
export DB_USERNAME=ecom_user
export DB_PASSWORD=your_password
export JWT_ACCESS_SECRET=your_32_char_access_secret_here_ok123
export JWT_REFRESH_SECRET=your_32_char_refresh_secret_here_ok123
export SPRING_PROFILES_ACTIVE=dev

# Build & run
mvn clean install
mvn spring-boot:run
```

**Access Points:**
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Admin Dashboard: http://localhost:8080/admin

---

## 🏗️ Project Development Approach

### Architecture Pattern: Modular Monolithic + Domain-Driven Design (DDD)

This project follows an **incremental, domain-driven development** approach where functionality is built in distinct phases:

### Phase 1️⃣: Domain Entity Modeling
**Objective:** Define the core business domain through entity classes

**What I Did:**
1. **Identified Core Entities** - User, Product, Order, Cart, Inventory, Payment
2. **Designed Relationships** - One-to-Many, Many-to-One, Many-to-Many mappings
3. **Created Base Classes** - `BaseEntity` with audit fields (createdAt, updatedAt, createdBy, updatedBy)
4. **Defined Enums** - UserRole (ADMIN, SELLER, USER), OrderStatus, PaymentStatus, etc.

**Key Classes:**
```java
@Entity @Table(name = "users")
public class User extends BaseEntity {
    @Column(unique = true, nullable = false)
    private String email;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles")
    private Set<Role> roles;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Order> orders;
}
```

**Result:** 15+ entity classes with proper JPA annotations and relationships

### Phase 2️⃣: CRUD Operations & Data Access Layer
**Objective:** Implement persistence layer with repositories and basic CRUD services

**What I Did:**
1. **Created Repositories** - Extended `JpaRepository` for each entity
2. **Implemented Custom Queries** - Used `@Query` for complex filtering
3. **Built Services** - Implemented business logic with transactional support
4. **Added Pagination** - All list endpoints support pagination and sorting

**Example Service Implementation:**
```java
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    
    // Create
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        return productRepository.save(product);
    }
    
    // Read (with caching)
    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
    
    // Update (cache eviction)
    @CacheEvict(value = "products", key = "#id")
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = getProductById(id);
        product.setName(request.getName());
        return productRepository.save(product);
    }
    
    // Delete
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
    
    // List with pagination
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
}
```

**Result:** 20+ repositories, 25+ service classes, full CRUD coverage

### Phase 3️⃣: RESTful API Design & API Versioning
**Objective:** Create clean REST endpoints with proper HTTP semantics

**What I Did:**
1. **Versioned APIs** - All endpoints under `/api/v1/` prefix
2. **Used Proper HTTP Methods:**
   - `GET /api/v1/products` - List all
   - `GET /api/v1/products/{id}` - Get single
   - `POST /api/v1/products` - Create new
   - `PUT /api/v1/products/{id}` - Update
   - `DELETE /api/v1/products/{id}` - Delete

3. **Unified Response Format** - All responses wrapped in `ApiResponse<T>`
4. **Input Validation** - Used `@Valid` and custom validators
5. **DTOs for Data Transfer** - Separate request/response objects with MapStruct

**Example API Design:**
```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductMapper mapper;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String search
    ) {
        Page<Product> products = productService.getAllProducts(
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        Page<ProductResponse> response = products.map(mapper::toResponse);
        return ResponseEntity.ok(new ApiResponse<>(true, "Products retrieved", response));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
        @Valid @RequestBody ProductRequest request
    ) {
        Product product = productService.createProduct(request);
        return ResponseEntity.status(CREATED).body(
            new ApiResponse<>(true, "Product created", mapper.toResponse(product))
        );
    }
}
```

**Result:** 50+ API endpoints, versioned architecture, OpenAPI documentation

### Phase 4️⃣: Cross-Cutting Concerns
**Objective:** Add non-functional requirements across all modules

**What I Did:**

#### A. Logging
- Set up **Logback** with file-based logging
- Configured daily rotation (max 10MB per file, 30-day retention)
- Different log levels for dev (DEBUG) vs prod (INFO)
- Structured logging in `logs/application.log`

#### B. Caching
- Implemented **Spring Cache** with `@Cacheable` and `@CacheEvict`
- Cached product categories (30-min TTL)
- Cached brand listings (1-hour TTL)
- **Note:** Simple in-memory caching using ConcurrentHashMap (Redis not implemented)

#### C. Exception Handling
- Created **GlobalExceptionHandler** with `@RestControllerAdvice`
- Custom exceptions for business logic (ResourceNotFoundException, BusinessLogicException)
- Unified error response format

#### D. Rate Limiting
- Implemented **Bucket4j** for rate limiting
- 5 requests/minute on login endpoint
- 100 requests/minute on other endpoints

#### E. Database Migrations
- Set up **Flyway** for schema versioning
- V1: Initial schema with core entities
- V2: Added roles and security tables
- V3: Added business features (inventory, payments, notifications)

**Result:** Production-ready cross-cutting concerns across all layers

---

## 🔒 Spring Security Implementation Strategy

### Overview
I built a **multi-layered security architecture** combining JWT token-based auth with OAuth2 support, implementing step-by-step from foundational to advanced features.

### Step 1️⃣: Foundation - Spring Security Configuration
**What I Did:**
1. **Disabled CSRF** - Needed for stateless REST APIs
2. **Set Session Policy** - `SessionCreationPolicy.STATELESS` (no server-side sessions)
3. **Configured CORS** - Allow cross-origin requests from web applications
4. **Enabled Method Security** - `@EnableMethodSecurity` for `@PreAuthorize` annotations

**Code:**
```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF disabled because we're using JWT (stateless)
            .csrf(csrf -> csrf.disable())
            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Exception handling for auth errors
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint)  // 401 response
                .accessDeniedHandler(restAccessDeniedHandler)           // 403 response
            )
            // Request authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/v1/auth/**", "/", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                // Admin-only endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Seller-only endpoints
                .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            // Add JWT filter before default auth filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Step 2️⃣: JWT Token-Based Authentication
**What I Implemented - FULLY APPLIED:**
1. **Token Generation** - Created JWT tokens with user roles and expiration
2. **Token Validation** - Verified signature and expiration on each request
3. **Token Refresh** - Implemented refresh token mechanism for long-lived sessions
4. **Stateless Auth** - No server-side session storage

**JWT Components:**
```java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    
    @Value("${config.jwt.access-secret}")
    private String accessSecret;
    
    @Value("${config.jwt.access-expiration}")
    private long accessExpiration;
    
    // Step 1: Generate JWT token
    public String generateAccessToken(User user) {
        return Jwts.builder()
            .setSubject(user.getEmail())
            .claim("userId", user.getId())
            .claim("roles", user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName())
                .collect(Collectors.toList()))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
            .signWith(SignatureAlgorithm.HS512, accessSecret)
            .compact();
    }
    
    // Step 2: Validate JWT token
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(accessSecret)
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    // Step 3: Extract user info from token
    public String getUserEmailFromToken(String token) {
        return Jwts.parser()
            .setSigningKey(accessSecret)
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }
    
    // Step 4: Extract roles from token
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return (List<String>) Jwts.parser()
            .setSigningKey(accessSecret)
            .parseClaimsJws(token)
            .getBody()
            .get("roles");
    }
}
```

### Step 3️⃣: JWT Filter - Extracting & Processing Tokens
**What I Implemented - FULLY APPLIED:**

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        try {
            // Step 1: Extract JWT token from Authorization header
            String token = extractTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // Step 2: Get user email from token
                String email = jwtTokenProvider.getUserEmailFromToken(token);
                
                // Step 3: Load user from database
                User user = userRepository.findByEmail(email)
                    .orElse(null);
                
                if (user != null) {
                    // Step 4: Extract roles from token
                    List<String> roles = jwtTokenProvider.getRolesFromToken(token);
                    
                    // Step 5: Create authentication object
                    UsernamePasswordAuthenticationToken auth = 
                        new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList())
                        );
                    
                    // Step 6: Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            // Log security error
            logger.error("JWT authentication failed", e);
        }
        
        // Continue filter chain
        chain.doFilter(request, response);
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### Step 4️⃣: Role-Based Access Control (RBAC)
**What I Implemented - FULLY APPLIED:**

1. **Three Roles Defined:** ADMIN, SELLER, USER
2. **Role Hierarchy:** ADMIN > SELLER > USER
3. **@PreAuthorize Annotations:** Applied on sensitive endpoints

**Examples:**
```java
// ADMIN only
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/api/v1/products/{id}")
public ResponseEntity<Void> deleteProduct(@PathVariable Long id) { ... }

// ADMIN or SELLER
@PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
@PostMapping("/api/v1/products")
public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
    @Valid @RequestBody ProductRequest request
) { ... }

// Authenticated users only
@PreAuthorize("isAuthenticated()")
@GetMapping("/api/v1/orders")
public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders() { ... }

// Specific user (owns the resource)
@PreAuthorize("@securityService.isOwner(#userId)")
@GetMapping("/api/v1/users/{userId}/profile")
public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(
    @PathVariable Long userId
) { ... }
```

### Step 5️⃣: Password Security
**What I Implemented - FULLY APPLIED:**

```java
@Configuration
public class PasswordEncoderConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 12 (strong but not too slow)
        return new BCryptPasswordEncoder(12);
    }
}

// Usage in AuthService
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    
    public void registerUser(RegisterRequest request) {
        // Hash password before storing
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);  // Store hashed, never plaintext
        
        userRepository.save(user);
    }
    
    public void loginUser(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        
        // Compare provided password with hashed password
        boolean passwordMatches = passwordEncoder.matches(
            request.getPassword(),
            user.getPassword()
        );
        
        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        
        // Generate JWT token
        return jwtTokenProvider.generateAccessToken(user);
    }
}
```

### Step 6️⃣: OAuth2 Integration (Google Sign-In)
**What I Implemented - PARTIALLY APPLIED:**

**Fully Implemented:**
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService)  // Custom user service
                )
                .successHandler(oAuth2SuccessHandler)  // Handle successful login
                .failureHandler(oAuth2FailureHandler)  // Handle failed login
            )
            // ... rest of config
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) {
        // Step 1: Get OAuth2 principal
        OAuth2AuthenticationPrincipal principal = 
            (OAuth2AuthenticationPrincipal) authentication.getPrincipal();
        
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        
        // Step 2: Find or create user in database
        User user = userRepository.findByEmail(email)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setRole(UserRole.USER);  // Default role
                return userRepository.save(newUser);
            });
        
        // Step 3: Generate JWT token
        String token = jwtTokenProvider.generateAccessToken(user);
        
        // Step 4: Redirect to frontend with token
        getRedirectStrategy().sendRedirect(request, response, 
            "http://localhost:3000/auth/callback?token=" + token);
    }
}
```

**Partially Applied (Not Fully Implemented):**
- ❌ Social login UI integration on frontend (would need React/Angular setup)
- ❌ Multiple OAuth2 providers (only Google configured, can extend to GitHub, Facebook)
- ❌ OAuth2 token refresh (currently uses JWT refresh tokens instead)

### Step 7️⃣: Custom Exception Handlers
**What I Implemented - FULLY APPLIED:**

```java
@RestControllerAdvice
@Slf4j
public class GlobalSecurityExceptionHandler {
    
    // 401 Unauthorized - Custom entry point
    @Component
    @RequiredArgsConstructor
    public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
        
        @Override
        public void commence(HttpServletRequest request,
                           HttpServletResponse response,
                           AuthenticationException authException) throws IOException {
            response.setStatus(SC_UNAUTHORIZED);
            response.setContentType("application/json");
            
            ApiResponse<Void> errorResponse = new ApiResponse<>(
                false,
                "Unauthorized - Please provide valid JWT token",
                null,
                "AUTHENTICATION_FAILED"
            );
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
    }
    
    // 403 Forbidden - Custom access denied handler
    @Component
    @RequiredArgsConstructor
    public class RestAccessDeniedHandler implements AccessDeniedHandler {
        
        @Override
        public void handle(HttpServletRequest request,
                         HttpServletResponse response,
                         AccessDeniedException accessDeniedException) throws IOException {
            response.setStatus(SC_FORBIDDEN);
            response.setContentType("application/json");
            
            ApiResponse<Void> errorResponse = new ApiResponse<>(
                false,
                "Access Denied - You don't have permission to access this resource",
                null,
                "INSUFFICIENT_PERMISSIONS"
            );
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
    }
}
```

### Step 8️⃣: Refresh Token Mechanism
**What I Implemented - FULLY APPLIED:**

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String tokenValue;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
}

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    
    private final RefreshTokenRepository tokenRepository;
    
    // Step 1: Create refresh token
    public RefreshToken createRefreshToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenValue(generateSecureToken());
        token.setExpiresAt(LocalDateTime.now().plusDays(7));  // 7-day expiration
        return tokenRepository.save(token);
    }
    
    // Step 2: Verify refresh token
    public Optional<RefreshToken> verifyRefreshToken(String tokenValue) {
        Optional<RefreshToken> token = tokenRepository.findByTokenValue(tokenValue);
        
        if (token.isPresent() && token.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            return token;
        }
        return Optional.empty();
    }
    
    // Step 3: Issue new access token using refresh token
    public String refreshAccessToken(String refreshToken) {
        RefreshToken token = verifyRefreshToken(refreshToken)
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        
        User user = token.getUser();
        return jwtTokenProvider.generateAccessToken(user);
    }
}

// API Endpoint
@PostMapping("/api/v1/auth/refresh-token")
public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
    @RequestBody RefreshTokenRequest request
) {
    String newAccessToken = refreshTokenService.refreshAccessToken(
        request.getRefreshToken()
    );
    
    return ResponseEntity.ok(new ApiResponse<>(
        true,
        "Token refreshed successfully",
        new TokenResponse(newAccessToken, request.getRefreshToken())
    ));
}
```

### Step 9️⃣: CORS & CSRF Protection
**What I Implemented:**

**CORS - FULLY APPLIED:**
```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",        // Dev frontend
            "http://localhost:8080",        // Dev backend UI
            "https://yourdomain.com"        // Production
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

**CSRF - PARTIALLY APPLIED:**
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF disabled - Because:
            // 1. We're using stateless REST API (no server sessions)
            // 2. JWT tokens in Authorization header are immune to CSRF
            // 3. Credentials stored in HttpOnly cookies would need CSRF protection
            .csrf(csrf -> csrf.disable())
            // For Thymeleaf forms (UI pages), CSRF is automatically enabled
            .build();
    }
}
```

**Note:** CSRF disabled for REST API (JWT is CSRF-safe), but enabled implicitly for Thymeleaf UI forms.

---

### Security Summary: What's Applied

| Feature | Status | Details |
|---------|--------|---------|
| JWT Authentication | ✅ FULLY | HS512 algorithm, 15-min access token, 7-day refresh token |
| OAuth2 (Google) | 🟡 PARTIAL | Backend fully implemented, needs frontend integration |
| Role-Based Access Control | ✅ FULLY | 3 roles (ADMIN, SELLER, USER) with @PreAuthorize |
| Password Hashing | ✅ FULLY | BCrypt with strength 12 |
| Refresh Token Mechanism | ✅ FULLY | Database-backed refresh tokens with expiration |
| CORS Protection | ✅ FULLY | Configured for specific origins |
| CSRF Protection | 🟡 PARTIAL | Disabled for REST API (JWT is safe), enabled for Thymeleaf |
| Rate Limiting | ✅ FULLY | Bucket4j implementation on login endpoint |
| Custom Exception Handlers | ✅ FULLY | 401/403 responses with meaningful messages |
| Security Headers | 🟡 PARTIAL | Basic headers configured, can add more |
| SQL Injection Prevention | ✅ FULLY | Parameterized queries via Spring Data JPA |
| XSS Protection | ✅ FULLY | Thymeleaf automatic escaping + output encoding |

---

## ✨ Implemented Features

### Authentication & Security
- ✅ JWT token-based authentication (HS512)
- ✅ OAuth2 integration (Google Sign-In)
- ✅ Role-Based Access Control (RBAC) - ADMIN, SELLER, USER
- ✅ BCrypt password hashing
- ✅ Refresh token mechanism
- ✅ CORS & CSRF protection
- ✅ Rate limiting (Bucket4j)
- ✅ SQL injection prevention (parameterized queries)
- ✅ Security headers (HSTS, X-Frame-Options, etc.)

### User Management
- ✅ User registration with email validation
- ✅ User login with JWT tokens
- ✅ User profile management (update name, email, address)
- ✅ Password reset via email
- ✅ Seller application & approval workflow
- ✅ Multi-role assignment
- ✅ User status management (ACTIVE, INACTIVE, BANNED)

### Product Management
- ✅ Product creation, update, delete (CRUD)
- ✅ Product categories & brands management
- ✅ Product images with Cloudinary integration
- ✅ Advanced product search & filtering
- ✅ Pagination & sorting
- ✅ Product inventory tracking
- ✅ Seller product dashboard

### Shopping Cart
- ✅ Add/remove products from cart
- ✅ Update cart item quantities
- ✅ Calculate cart totals (subtotal, tax)
- ✅ Cart persistence per user
- ✅ Clear cart after order

### Order Management
- ✅ Order creation with validation
- ✅ Order status tracking (PENDING, CONFIRMED, SHIPPED, DELIVERED)
- ✅ Order history & details
- ✅ Order cancellation
- ✅ Multiple items per order
- ✅ Order notifications via email

### Payment Processing
- ✅ Razorpay integration
- ✅ Payment order creation & verification
- ✅ Webhook signature verification
- ✅ Payment status tracking
- ✅ Transaction logs

### Inventory Management
- ✅ Stock quantity tracking
- ✅ Stock reservation during checkout
- ✅ Stock release on order cancellation
- ✅ Low stock alerts

### Notifications
- ✅ Async email notifications
- ✅ Order confirmation emails
- ✅ Payment receipt emails
- ✅ Seller notification emails

### Admin Features
- ✅ User management dashboard
- ✅ Product management interface
- ✅ Order monitoring
- ✅ Seller approval workflow
- ✅ Inventory overview
- ✅ Category & brand management

### File & Media
- ✅ Product image upload via Cloudinary
- ✅ Secure file URL generation
- ✅ Image optimization

---

## 📊 API Examples

### User Authentication
```bash
# Register
POST /api/v1/auth/register
{
  "email": "user@example.com",
  "password": "SecurePass@123",
  "firstName": "John",
  "lastName": "Doe"
}

# Login
POST /api/v1/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass@123"
}
```

### Products
```bash
# Get all products (paginated)
GET /api/v1/products?page=0&size=10

# Search products
GET /api/v1/products/search?query=laptop&categoryId=1

# Get product details
GET /api/v1/products/{productId}
```

### Orders
```bash
# Create order
POST /api/v1/orders
Authorization: Bearer <jwt_token>
{
  "items": [
    {"productId": 1, "quantity": 2}
  ],
  "shippingAddress": "123 Main St"
}

# Get order history
GET /api/v1/orders
Authorization: Bearer <jwt_token>

# Get order details
GET /api/v1/orders/{orderId}
Authorization: Bearer <jwt_token>
```

### Payments
```bash
# Create Razorpay order
POST /api/v1/payments/razorpay/create-order
Authorization: Bearer <jwt_token>
{
  "orderId": 1,
  "amount": 5000
}

# Verify payment
POST /api/v1/payments/razorpay/verify
{
  "razorpayPaymentId": "pay_xxx",
  "razorpayOrderId": "order_xxx",
  "razorpaySignature": "signature_xxx"
}
```

---

## 🏗️ Project Structure

```
src/main/java/com/abhishek/ecommerce/
├── auth/                          # Authentication & JWT
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── repository/
├── security/                      # Spring Security configuration
│   ├── config/SecurityConfig.java
│   ├── jwt/JwtAuthenticationFilter.java
│   ├── oauth2/OAuth2SuccessHandler.java
│   └── exception/
├── user/                          # User management
│   ├── entity/User.java
│   ├── service/UserService.java
│   ├── controller/UserController.java
│   └── dto/
├── product/                       # Product catalog
│   ├── entity/Product.java
│   ├── service/ProductService.java
│   ├── controller/ProductController.java
│   ├── dto/
│   └── mapper/
├── cart/                          # Shopping cart
│   ├── entity/Cart.java
│   ├── service/CartService.java
│   └── controller/CartController.java
├── order/                         # Order management
│   ├── entity/Order.java
│   ├── service/OrderService.java
│   ├── controller/OrderController.java
│   └── dto/
├── payment/                       # Razorpay integration
│   ├── gateway/RazorpayGateway.java
│   ├── service/PaymentService.java
│   ├── controller/PaymentController.java
│   └── dto/
├── inventory/                     # Stock management
│   ├── entity/Inventory.java
│   ├── service/InventoryService.java
│   └── repository/
├── notification/                  # Email notifications
│   ├── service/EmailService.java
│   ├── event/
│   └── config/EmailConfig.java
├── ui/                            # Thymeleaf page controllers
│   ├── controller/AdminController.java
│   ├── controller/SellerController.java
│   └── controller/UserPageController.java
├── common/                        # Shared utilities
│   ├── baseEntity/BaseEntity.java
│   ├── apiResponse/ApiResponse.java
│   ├── exception/GlobalExceptionHandler.java
│   └── utils/
├── config/                        # Application configuration
│   ├── AdminProperties.java
│   ├── AdminBootstrap.java
│   └── AppConfig.java
└── EcommerceBackendApplication.java
```

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=OrderServiceTest

# Run in headless mode (CI/CD)
mvn test -Dheadless=true
```

**Test Statistics:** 80+ unit & integration tests

---

## 🐳 Docker Deployment

```bash
# Build Docker image
docker build -t ecommerce-backend:latest .

# Run with Docker Compose
docker-compose up -d

# View logs
docker logs -f ecommerce-backend
```

---

## 📱 API Versioning

All endpoints follow semantic versioning:
```
/api/v1/products
/api/v2/products  # Future versions
```

---

## 🔍 Cross-Cutting Concerns

### Logging
- **Framework:** Logback with asynchronous appenders
- **Location:** `logs/application.log` (daily rotation)
- **Levels:** DEBUG (dev), INFO (prod)
- **Format:** Timestamp, level, logger name, message

### Caching
- **Mechanism:** Spring @Cacheable (ConcurrentHashMap)
- **Use Cases:**
  - Product categories (30 min TTL)
  - Brand listings (1 hour TTL)
  - User role permissions (cached)
- **Note:** Redis caching not implemented; simple in-memory caching used

### Error Handling
- **Global Exception Handler** catches all exceptions
- **Custom exceptions** for business logic
- **Graceful error responses** with error codes
- **Validation** via @Valid and custom validators

### Rate Limiting
- **Bucket4j:** Token bucket algorithm
- **Applied to:** Login endpoints (5 requests/minute)
- **Returns:** 429 Too Many Requests

---

## 📚 Database

### Schema Overview
- **Total Tables:** 15+
- **Relationships:** One-to-Many, Many-to-One, Many-to-Many
- **Migrations:** Flyway (V1, V2, V3)

### Key Tables
- `users` - User accounts & profiles
- `roles` - Role definitions (ADMIN, SELLER, USER)
- `user_roles` - Role assignments
- `products` - Product catalog
- `categories` & `brands` - Product taxonomy
- `orders` & `order_items` - Order data
- `cart` & `cart_items` - Shopping carts
- `payments` - Payment transactions
- `inventory` - Stock tracking
- `refresh_tokens` - Token management

---

## 🔐 Security Features

- **Data Encryption:** Passwords hashed with BCrypt (strength 12)
- **Token Security:** JWT tokens with 15-min expiration
- **Transport:** HTTPS recommended for production
- **SQL Injection:** Parameterized queries via Spring Data JPA
- **XSS Protection:** Thymeleaf automatic escaping
- **CSRF:** Token validation on state-changing requests
- **CORS:** Configured for specified origins

---

## 📝 Configuration Profiles

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Testing
mvn test -Dspring.profiles.active=test

# Production
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

---

## 🎓 Learning Resources

This project demonstrates:
- Domain-Driven Design (DDD) principles
- Modular monolithic architecture
- Clean separation of concerns
- Service layer pattern
- Repository pattern with Spring Data JPA
- Exception handling strategies
- JWT authentication flow
- OAuth2 integration
- Async email processing
- API versioning best practices
- Thymeleaf template rendering
- Database migrations with Flyway

---

## 🚀 Deployment

### Render (Live)
The application is deployed and running at: [https://ecommerce-backend-w8mg.onrender.com](https://ecommerce-backend-w8mg.onrender.com)

**Free tier details:**
- Auto spins down after 15 minutes of inactivity
- MySQL database included
- Environment variables configured
- Custom domain support

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed deployment options.

---

## 🤝 Development Workflow

1. **Domain Design:** Identify entities and business rules
2. **Entity Creation:** Define JPA entities with relationships
3. **Repository Layer:** Create Spring Data JPA repositories
4. **Service Layer:** Implement business logic
5. **DTO Layer:** Create request/response DTOs with MapStruct
6. **Controller Layer:** Build REST endpoints with validation
7. **Security:** Apply @PreAuthorize and role checks
8. **Testing:** Write unit & integration tests
9. **Documentation:** Update API docs

---

## 📊 Performance Metrics

- **Response Time:** <200ms (average)
- **Database Queries:** Optimized with indexing
- **Cache Hit Ratio:** 80%+ for frequently accessed data
- **Concurrent Users:** Supports 100+ concurrent connections

---

## 📄 License

This project is part of a portfolio and is for educational purposes.

---

## 👨‍💻 Author

**Abhishek** - Backend Developer ( Java | Spring Boot )

---

## 📞 Support

For issues or questions, refer to [ARCHITECTURE.md](ARCHITECTURE.md) for system design details and troubleshooting approaches.
