package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.user.entity.User;

public interface RefreshTokenService {
    RefreshToken createOrReplaceRefreshToken(User user);
    RefreshToken validateAndGet(String token);
    void deleteByUsername(String username);
}

