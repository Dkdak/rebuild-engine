# Rebuild Application Architecture

본 프로젝트는 **Spring Boot**와 **Spring Data JPA(Hibernate)** 기반으로 구축되었으며, 각 계층(Layer) 간의 역할을 엄격히 분리하여 결합도를 낮추고 유지보수성을 극대화한 **계층형 아키텍처(Layered Architecture)**를 따릅니다.

---

## 1. 패키지 구조 (Package Structure)

데이터 관련 모델들을 `model` 패키지 내부로 응집력 있게 통합하고, API의 입력(`request`)과 출력(`response`)을 물리적으로 분리하여 관리합니다.

```text
├── rebuild-engine/
│   ├── Dockerfile
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   ├── gradle/
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   └── src/
│       ├── controller    # API 엔드포인트 정의 및 HTTP 요청/응답 제어
│       ├── exception     # 글로벌 및 커스텀 예외(Exception) 처리
│       ├── model         # 📦 데이터 모델 관련 패키지 통합 (Core Domain & DTO)
│       │   ├── entity    # 데이터베이스 테이블과 1:1 매핑되는 JPA 영속성 객체
│       │   ├── request   # 클라이언트의 요청 데이터를 받는 DTO
│       │   └── response  # 클라이언트에게 반환할 응답 데이터를 담는 DTO
│       ├── repository    # Spring Data JPA 기반의 데이터 액세스 계층 (DB 인터페이스)
│       ├── security      # 인증 및 인가 (Spring Security, JWT 등) 관련 설정
│       ├── service       # 핵심 비즈니스 로직 수행 및 트랜잭션 단위 제어
│       └── utils         # 공통적으로 사용되는 유틸리티 클래스
```


##  2. 데이터 흐름 및 레이어 역할 (Data Flow)
본 프레임워크 구조는 "엔티티(Entity)는 하위 계층(DTO, Controller)의 존재를 절대 모른다"는 단방향 의존성 규칙을 철저히 준수합니다.
```text
[클라이언트] ──(Request DTO)──> [Controller] ──(Request DTO)──> [Service]
                                                                  │
                                                              (Entity 변환/조회)
                                                                  │
                                                                  ▼
[클라이언트] <──(Response DTO)── [Controller] <──(Response DTO)── [Repository / DB]
```

🔹 Controller (표현 계층)
클라이언트의 요청을 받고 응답을 반환하는 진입점입니다.

철칙: 오직 request DTO와 response DTO만 취급하며, Entity 객체를 절대 직접 다루거나 외부에 노출하지 않습니다.

🔹 Service (비즈니스 로직 계층)
애플리케이션의 핵심 비즈니스 로직과 데이터 흐름을 제어합니다.

@Transactional을 통해 데이터베이스 트랜잭션 단위를 관리합니다.

Repository로부터 영속 상태의 Entity를 조회하여 필요한 비즈니스 행위를 수행하고, 결과를 DTO로 변환하여 Controller로 넘겨줍니다.

🔹 Repository (데이터 액세스 계층)
영속성 컨텍스트를 통해 Entity를 데이터베이스에 영속화하거나 조회하는 역할을 담당합니다.

🔹 Model (데이터 모델 계층)
entity (도메인 영속 객체): DB 테이블 스펙과 동치이며 하이버네이트에 의해 관리됩니다. 수시로 변하는 화면(API) 스펙에 오염되지 않도록 가장 순수하게 유지되어야 합니다. 데이터 수정을 위한 내부 비즈니스 메서드만 포함합니다.

request (입력 DTO): 클라이언트가 보내는 JSON 데이터를 매핑합니다. 내부적으로 엔티티를 조립하는 toEntity() 메서드를 가질 수 있습니다.

response (출력 DTO): API 응답 스펙입니다. 안전하게 엔티티의 데이터를 복사(카피)해 오는 정적 팩토리 메서드 from(Entity)를 가질 수 있습니다.


## 3. 핵심 구현 가이드라인 (Code Example)
test_property 테이블을 기준으로 하는 표준 개발 패턴입니다. 새로운 기능을 만들 때 이 패턴을 준수합니다.

① Request DTO ➡️ Entity 변환 (저장 시)
```Java
// model/request/TestPropertySaveRequest.java
public class TestPropertySaveRequest {
private String name;

    // DTO에서 엔티티를 안전하게 조립하여 반환
    public TestPropertyEntity toEntity() {
        return TestPropertyEntity.builder()
                .name(this.name)
                .build();
    }
}
```

② Entity ➡️ Response DTO 변환 (조회 시)
```Java
// model/response/TestPropertyResponse.java
public class TestPropertyResponse {
private Long id;
private String name;

    // 정적 팩토리 메서드를 통해 엔티티 데이터를 DTO에 안전하게 복사
    public static TestPropertyResponse from(TestPropertyEntity entity) {
        return new TestPropertyResponse(entity.getId(), entity.getName());
    }
}
```

③ Service에서의 제어 패턴
```Java
// service/TestPropertyService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestPropertyService {

    private final TestPropertyRepository testPropertyRepository;

    @Transactional // 등록/수정 시 롤백 및 쓰기 트랜잭션 적용
    public TestPropertyResponse saveProperty(TestPropertySaveRequest requestDto) {
        // 1. DTO를 엔티티로 변환
        TestPropertyEntity entity = requestDto.toEntity();
        // 2. 저장 (영속화)
        TestPropertyEntity savedEntity = testPropertyRepository.save(entity);
        // 3. 결과를 Response DTO로 카피하여 반환
        return TestPropertyResponse.from(savedEntity);
    }

    public TestPropertyResponse getProperty(Long id) {
        // 1. 엔티티 단건 조회
        TestPropertyEntity entity = testPropertyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로퍼티입니다."));
        // 2. 결과를 DTO로 카피하여 반환
        return TestPropertyResponse.from(entity);
    }
}
```
##  4. 이 구조를 통해 얻는 이점
유지보수성 향상: 프론트엔드의 화면 요구사항(DTO)이 변경되어도 데이터베이스 테이블 스펙(Entity)이나 핵심 비즈니스 로직이 깨지지 않습니다.

안전성 확보: JPA의 비결정적인 프록시 객체나 지연 로딩(Lazy Loading)으로 인해 발생하는 예외 상황을 Controller단 이전에 차단할 수 있습니다.

높은 가독성: 패키지만 보아도 어떤 클래스가 어떤 역할을 하는지 한눈에 파악할 수 있어 협업에 유리합니다.



---

## 5. 심화 개발 가이드라인 & 금지 조항 (Advanced Rules)

안정적인 JPA 영속성 관리와 데이터 무결성을 위해 모든 개발자는 다음 규칙을 반드시 준수해야 합니다.

### ① Entity 수정 시 변경 감지(Dirty Checking) 활용
* 엔티티의 값을 수정할 때 `repository.save()`를 호출하지 않습니다.
* `@Transactional`이 적용된 Service 메서드 내에서 영속성 객체의 값만 변경하면, 메서드 종료 시 하이버네이트가 변경을 감지하여 자동으로 `UPDATE` 쿼리를 실행합니다.

```java
// 올바른 수정 패턴
@Transactional
public void updatePropertyName(Long id, String newName) {
    TestPropertyEntity entity = testPropertyRepository.findById(id).orElseThrow();
    entity.updateName(newName); // 핵심 비즈니스 메서드 호출 (save 호출 X)
}

```
② Entity 내 롬복(Lombok) 어노테이션 사용 제한
@Data 사용 금지: @Data에는 @Setter와 무분별한 toString(), equals()가 포함되어 있어 JPA 엔티티 간 양방향 연관관계 시 무한 루프나 데이터 오염을 유발합니다.

@Setter 사용 금지: 객체의 값은 부호 있는 비즈니스 메서드(예: updateName())를 통해서만 변경되어야 하며, 일괄 생성은 @Builder를 활용합니다.

기본 생성자 제한: JPA 스펙을 위한 기본 생성자는 외부 생성을 막기 위해 access = AccessLevel.PROTECTED로 제한합니다.

③ 지연 로딩(Lazy Loading) 및 프록시 예외 방지
연관된 엔티티를 함께 조회하여 DTO로 변환해야 하는 경우, 연관 객체에 대한 접근(Getter 호출 및 DTO 카피)은 반드시 트랜잭션(@Transactional)이 살아있는 Service 레이어 내부에서 완료되어야 합니다.

트랜잭션이 종료된 Controller 레이어에서 연관 엔티티를 DTO로 변환하려고 하면 LazyInitializationException 에러가 발생합니다.
