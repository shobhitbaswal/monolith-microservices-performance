package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.OAuthResponseDto;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface OAuthService {

    OAuthResponseDto handleOAuthLogin(OAuth2User oAuth2User, String provider);
}