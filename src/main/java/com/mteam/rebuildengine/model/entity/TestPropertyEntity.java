package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_property")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙상 기본 생성자 필요 (무분별한 생성 방지)
public class TestPropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL 대응
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder
    public TestPropertyEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    // 서비스 레이어에서 엔티티를 수정할 때 사용할 비즈니스 메서드
    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("이름은 비어있을 수 없습니다.");
        }
        this.name = newName;
    }
}
