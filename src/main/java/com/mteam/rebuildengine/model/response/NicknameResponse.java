package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.UserEntity;

public class NicknameResponse {
    private final String nickname;

    private NicknameResponse(String nickname) {
        this.nickname = nickname;
    }

    public static NicknameResponse from(UserEntity entity) {
        return new NicknameResponse(entity.getNickname());
    }

    public String getNickname() {
        return nickname;
    }
}
