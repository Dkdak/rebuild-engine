package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.TestPropertyEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestPropertyResponse {
    private Long id;
    private String name;

    // 엔티티를 안전하게 DTO로 카피(변환)하는 메서드
    public static TestPropertyResponse from(TestPropertyEntity entity) {
        return new TestPropertyResponse(entity.getId(), entity.getName());
    }
}