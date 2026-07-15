package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.TestPropertyEntity;
import com.mteam.rebuildengine.repository.TestPropertyRepository;
import com.mteam.rebuildengine.model.request.TestPropertySaveRequest;
import com.mteam.rebuildengine.model.response.TestPropertyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 읽기 전용 기본 설정 (성능 최적화)
public class TestPropertyServiceImpl implements TestPropertyService {
    private final TestPropertyRepository testPropertyRepository;

    /**
     * 1. 저장 기능
     */
    @Override
    @Transactional // 쓰기 작업이므로 가묵적 트랜잭션 적용
    public TestPropertyResponse saveProperty(TestPropertySaveRequest requestDto) {
        // Request DTO의 데이터를 꺼내 엔티티 생성
        TestPropertyEntity entity = TestPropertyEntity.builder()
                .name(requestDto.getName())
                .build();

        // 영속성 컨텍스트에 저장
        TestPropertyEntity savedEntity = testPropertyRepository.save(entity);

        // 저장된 엔티티를 Response DTO에 카피하여 반환
        return TestPropertyResponse.from(savedEntity);
    }

    /**
     * 2. 단건 조회 기능
     */
    @Override
    public TestPropertyResponse getProperty(Long id) {
        // 레포지토리에서는 엔티티를 영속성 컨텍스트를 통해 조회
        TestPropertyEntity entity = testPropertyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로퍼티가 존재하지 않습니다. ID: " + id));

        // 조회된 엔티티를 DTO에 카피하여 안전하게 반환
        return TestPropertyResponse.from(entity);
    }

    @Override
    public List<TestPropertyResponse> getFindAll() {
        // 레포지토리에서는 엔티티를 영속성 컨텍스트를 통해 조회
        return testPropertyRepository.findAll()
                .stream()
                .map(TestPropertyResponse::from)
                .toList();

    }

}
