package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.request.LoginRequest;
import com.mteam.rebuildengine.model.request.SignupRequest;
import com.mteam.rebuildengine.model.request.WithdrawRequest;
import com.mteam.rebuildengine.model.response.AuthResponse;
import com.mteam.rebuildengine.model.response.CurrentUserResponse;
import com.mteam.rebuildengine.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getMe(authentication.getName()));
    }

    // 1단계: 서버 측 토큰 무효화 없이 클라이언트 localStorage 삭제만으로 처리 (FEATURE_02_AUTH.md §3.1)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(Authentication authentication, @RequestBody WithdrawRequest request) {
        authService.withdraw(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
