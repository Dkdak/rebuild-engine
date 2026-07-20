package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.UserEntity;

public class AuthResponse {
    private final String token;
    private final String email;

    private AuthResponse(String token, String email) {
        this.token = token;
        this.email = email;
    }

    public static AuthResponse of(String token, UserEntity entity) {
        return new AuthResponse(token, entity.getEmail());
    }

    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }
}
