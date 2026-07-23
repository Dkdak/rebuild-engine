package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public UserEntity(Long id, String email, String password, String nickname, Role role, boolean isDeleted) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.isDeleted = isDeleted;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 회원탈퇴: 논리 삭제 처리 (FEATURE_02_AUTH.md §3.1)
    public void markDeleted() {
        this.isDeleted = true;
    }

    // 논리 삭제된 이메일로 재가입 시 기존 row를 재사용 (FEATURE_02_AUTH.md §3.1)
    public void reactivate(String encodedPassword, String nickname) {
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = Role.USER;
        this.isDeleted = false;
    }

    // 회원정보 수정: 닉네임 변경 (FEATURE_02_AUTH.md §3.1 PATCH /api/v1/auth/me)
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
