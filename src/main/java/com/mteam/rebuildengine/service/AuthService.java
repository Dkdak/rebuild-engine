package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.request.LoginRequest;
import com.mteam.rebuildengine.model.request.SignupRequest;
import com.mteam.rebuildengine.model.request.WithdrawRequest;
import com.mteam.rebuildengine.model.response.AuthResponse;
import com.mteam.rebuildengine.model.response.CurrentUserResponse;

public interface AuthService {
    AuthResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    CurrentUserResponse getMe(String email);
    void withdraw(String email, WithdrawRequest request);
}
