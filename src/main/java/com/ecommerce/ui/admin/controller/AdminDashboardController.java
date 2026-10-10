package com.ecommerce.ui.admin.controller;

import com.ecommerce.order.service.OrderService;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin Dashboard Controller
 * ROLE_ADMIN only - enforced by SecurityConfig
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;

    /**
     * Admin Dashboard - Main statistics page
     */
    @GetMapping
    public String dashboard(Model model) {
        try {
            // Fetch statistics
            long totalUsers = userService.getTotalUserCount();
            long totalSellers = userService.getTotalSellerCount();
            long pendingSellerRequests = userService.getPendingSellerRequestCount();
            long totalProducts = productService.getTotalProductCount();
            long totalOrders = orderService.getTotalOrderCount();
            long pendingOrders = orderService.getPendingOrderCount();

            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("totalSellers", totalSellers);
            model.addAttribute("pendingSellerRequests", pendingSellerRequests);
            model.addAttribute("totalProducts", totalProducts);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("pendingOrders", pendingOrders);

            log.info("Admin dashboard loaded - Users: {}, Sellers: {}, Products: {}, Orders: {}", 
                    totalUsers, totalSellers, totalProducts, totalOrders);
            return "admin/dashboard";
        } catch (Exception e) {
            log.error("Error loading admin dashboard", e);
            model.addAttribute("errorMessage", "Unable to load dashboard. Please try again.");
            return "admin/dashboard";
        }
    }
}
