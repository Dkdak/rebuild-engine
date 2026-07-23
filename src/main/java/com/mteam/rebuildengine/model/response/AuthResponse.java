package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.UserEntity;

public class AuthResponse {
    private final String token;
    private final String email;
    private final String nickname;

    private AuthResponse(String token, String email, String nickname) {
        this.token = token;
        this.email = email;
        this.nickname = nickname;
    }

    public static AuthResponse of(String token, UserEntity entity) {
        return new AuthResponse(token, entity.getEmail(), entity.getNickname());
    }

    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}
