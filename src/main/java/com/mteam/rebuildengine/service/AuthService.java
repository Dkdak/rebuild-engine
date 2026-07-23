package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.request.LoginRequest;
import com.mteam.rebuildengine.model.request.SignupRequest;
import com.mteam.rebuildengine.model.request.UpdateNicknameRequest;
import com.mteam.rebuildengine.model.request.WithdrawRequest;
import com.mteam.rebuildengine.model.response.AuthResponse;
import com.mteam.rebuildengine.model.response.CurrentUserResponse;
import com.mteam.rebuildengine.model.response.NicknameResponse;

public interface AuthService {
    AuthResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    CurrentUserResponse getMe(String email);
    NicknameResponse updateNickname(String email, UpdateNicknameRequest request);
    void withdraw(String email, WithdrawRequest request);
}
