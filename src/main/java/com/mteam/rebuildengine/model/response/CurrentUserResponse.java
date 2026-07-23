package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.UserEntity;

public class CurrentUserResponse {
    private final String email;
    private final String nickname;

    private CurrentUserResponse(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    public static CurrentUserResponse from(UserEntity entity) {
        return new CurrentUserResponse(entity.getEmail(), entity.getNickname());
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }
}
