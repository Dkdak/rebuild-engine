package com.mteam.rebuildengine.model.request;

import com.mteam.rebuildengine.model.entity.TestPropertyEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TestPropertySaveRequest {
    private String name;

    // 💡 DTO에서 엔티티를 생성하여 반환 (카피)
    public TestPropertyEntity toEntity() {
        return TestPropertyEntity.builder()
                .name(this.name)
                .build();
    }

}
