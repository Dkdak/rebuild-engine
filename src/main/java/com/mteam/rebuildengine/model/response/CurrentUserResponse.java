package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.UserEntity;

public class CurrentUserResponse {
    private final String email;

    private CurrentUserResponse(String email) {
        this.email = email;
    }

    public static CurrentUserResponse from(UserEntity entity) {
        return new CurrentUserResponse(entity.getEmail());
    }

    public String getEmail() {
        return email;
    }
}
