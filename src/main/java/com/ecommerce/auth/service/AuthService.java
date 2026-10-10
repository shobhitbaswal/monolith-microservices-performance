package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponseDto;
import com.ecommerce.auth.dto.LoginRequestDto;
import com.ecommerce.auth.dto.SignupRequestDto;
import com.ecommerce.auth.dto.SignupResponseDto;

public interface AuthService {

    SignupResponseDto signup(SignupRequestDto request);

    AuthResponseDto login(LoginRequestDto request);
}