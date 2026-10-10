package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.RefreshRequestDto;
import com.ecommerce.auth.dto.AuthResponseDto;
import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.service.RefreshTokenService;
import com.ecommerce.security.jwt.JwtUtil;
import com.ecommerce.common.apiResponse.ApiResponse;
import com.ecommerce.common.apiResponse.ApiResponseBuilder;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.shared.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "JWT login, refresh token, OAuth2 login")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class RefreshController {

    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Operation(
        summary = "Refresh access token",
        description = "Generates new access token using valid refresh token"
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refresh(@RequestBody RefreshRequestDto request) {
        String provided = request.getRefreshToken();
        RefreshToken rt = refreshTokenService.validateAndGet(provided);

        String username = rt.getUsername();
        
        // Get user to retrieve role and userId
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String newAccess = jwtUtil.generateToken(username, user.getRoles().stream().map(Role::name).toList());

        // replace persisted refresh token
        RefreshToken newRefreshTokenEntity = refreshTokenService.createOrReplaceRefreshToken(user);
        String newRefresh = newRefreshTokenEntity.getToken();

        AuthResponseDto dto = AuthResponseDto.builder()
                .token(newAccess)
                .userId(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.name().replace("ROLE_", "")).collect(java.util.stream.Collectors.toSet())) // Convert ROLE_USER to USER
                .refreshToken(newRefresh)
                .refreshTokenExpiryMs(newRefreshTokenEntity.getExpiresAt().toEpochMilli())
                .tokenType("Bearer")
                .build();

        return ResponseEntity.ok(ApiResponseBuilder.success("Token refreshed", dto));
    }
}
