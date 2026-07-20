package com.mteam.rebuildengine.model.request;

import com.mteam.rebuildengine.model.entity.Role;
import com.mteam.rebuildengine.model.entity.UserEntity;

public class SignupRequest {
    private String email;
    private String password;
    private boolean agreedToTerms;

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAgreedToTerms() {
        return agreedToTerms;
    }

    // DTO에서 엔티티를 안전하게 조립 (비밀번호는 Service에서 암호화된 값을 전달받는다)
    public UserEntity toEntity(String encodedPassword) {
        return UserEntity.builder()
                .email(this.email)
                .password(encodedPassword)
                .role(Role.USER)
                .isDeleted(false)
                .build();
    }
}
