# README.md - rebuild-engine

리모델링 투자 분석 엔진 (Spring Boot 기반 Core Engine)

이 프로젝트는 rebuild-frontend와 연동되는 백엔드 분석 엔진이며,
현재는 MVP + Mock 기반 구조 + 아키텍처 정의 단계입니다.

---

## 1. 프로젝트 목적
- 엔진 구조 정의
- frontend와 API 계약(Contract) 확립
- 데이터 흐름 및 레이어 표준화
- 향후 AI / 공공데이터 / 분석 엔진 확장 기반 설계


## 2. 패키지 구조 (Package Structure)

데이터 관련 모델들을 `model` 패키지 내부로 응집력 있게 통합하고, API의 입력(`request`)과 출력(`response`)을 물리적으로 분리하여 관리합니다.

```text
rebuild-engine/
├── Dockerfile
├── build.gradle
├── settings.gradle
├── gradlew
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── config        # 전역 설정 (Bean 등록, CORS, Security 등 Configuration 클래스)
    ├── controller    # API 엔드포인트 정의 및 HTTP 요청/응답 제어
    ├── exception     # 글로벌 및 커스텀 예외 처리
    ├── model         # 데이터 모델 통합 영역
    │   ├── entity    # JPA 영속성 객체 (DB 테이블 1:1 매핑)
    │   ├── request   # 요청 DTO
    │   └── response  # 응답 DTO
    ├── repository    # Spring Data JPA 데이터 접근 계층
    ├── security      # JWT / 인증 / 인가 설정
    ├── service       # 비즈니스 로직 및 트랜잭션 제어
    └── utils         # 공통 유틸리티
```
---

##  3. 데이터 흐름 및 레이어 역할 (Data Flow)

본 구조는 단방향 의존성 원칙을 강제합니다.

Entity는 절대 상위 계층(Controller/DTO)을 알지 못한다.

```text
[클라이언트] ──(Request DTO)──> [Controller] ──(Request DTO)──> [Service]
                                                                  │
                                                              (Entity 변환/조회)
                                                                  │
                                                                  ▼
[클라이언트] <──(Response DTO)── [Controller] <──(Response DTO)── [Repository / DB]
```
---

##  4. 계층별 역활 정의
### Controller (표현 계층)
클라이언트의 요청을 받고 응답을 반환하는 진입점입니다.
- HTTP 요청/응답 담당
- Request DTO / Response DTO만 사용
- Entity 직접 사용 금지
- 비즈니스 로직 금지

### Service (비즈니스 로직 계층)
애플리케이션의 핵심 비즈니스 로직과 데이터 흐름을 제어합니다.
- 핵심 비즈니스 로직 수행
- @Transactional 관리
- Repository로부터 영속 상태의 Entity 조회 및 변환
- DTO ↔ Entity 변환 수행

### Repository (데이터 액세스 계층)
영속성 컨텍스트를 통해 Entity를 데이터베이스에 영속화하거나 조회하는 역할을 담당합니다.
- JPA 기반 데이터 접근
- 저장 / 조회만 담당
- 계산 및 비즈니스 로직 금지
- DTO 생성 금지

### Model (데이터 모델 계층)

entity (도메인 영속 객체):
- DB 테이블 스펙과 동치이며 하이버네이트에 의해 관리
- 수시로 변하는 화면(API) 스펙에 오염되지 않도록 가장 순수하게 유지
- 데이터 수정을 위한 내부 비즈니스 메서드만 포함
- Setter 금지 / @Data 금지

request (입력 DTO):
- 클라이언트가 보내는 JSON 데이터를 매핑
- 내부적으로 엔티티를 조립하는 toEntity() 메서드를 포함 가능.

response (출력 DTO):
- API 응답 스펙
- 안전하게 엔티티의 데이터를 복사(카피)해 오는 정적 팩토리 메서드 from(Entity) 패턴 사용

---

## 5. 표준 코드 패턴 (Code Example)
test_property 테이블을 기준으로 하는 표준 개발 패턴입니다. 새로운 기능을 만들 때 이 패턴을 준수합니다.

### 5.1 Request DTO → Entity 변환 (저장)
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

### 5.2 Entity → Response DTO 변환 (조회)
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

### 5.3 Service 처리 패턴

Service는 인터페이스(계약)와 구현체로 분리합니다. Controller는 인터페이스 타입만 주입받으므로, 구현 로직이 바뀌어도 Controller는 수정하지 않습니다.

```Java
// service/TestPropertyService.java (인터페이스)
public interface TestPropertyService {
    TestPropertyResponse saveProperty(TestPropertySaveRequest requestDto);
    TestPropertyResponse getProperty(Long id);
}
```

```Java
// service/TestPropertyServiceImpl.java (구현체)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestPropertyServiceImpl implements TestPropertyService {

    private final TestPropertyRepository testPropertyRepository;

    @Override
    @Transactional // 등록/수정 시 롤백 및 쓰기 트랜잭션 적용
    public TestPropertyResponse saveProperty(TestPropertySaveRequest requestDto) {
        // 1. DTO를 엔티티로 변환
        TestPropertyEntity entity = requestDto.toEntity();
        // 2. 저장 (영속화)
        TestPropertyEntity savedEntity = testPropertyRepository.save(entity);
        // 3. 결과를 Response DTO로 카피하여 반환
        return TestPropertyResponse.from(savedEntity);
    }

    @Override
    public TestPropertyResponse getProperty(Long id) {
        // 1. 엔티티 단건 조회
        TestPropertyEntity entity = testPropertyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로퍼티입니다."));
        // 2. 결과를 DTO로 카피하여 반환
        return TestPropertyResponse.from(entity);
    }
}
```
---

##  6. 이 구조를 통해 얻는 이점
- 유지보수성 향상: 프론트엔드의 화면 요구사항(DTO)이 변경되어도 데이터베이스 테이블 스펙(Entity)이나 핵심 비즈니스 로직이 깨지지 않습니다.
- 안전성 확보: JPA의 비결정적인 프록시 객체나 지연 로딩(Lazy Loading)으로 인해 발생하는 예외 상황을 Controller단 이전에 차단할 수 있습니다.
- 높은 가독성: 패키지만 보아도 어떤 클래스가 어떤 역할을 하는지 한눈에 파악할 수 있어 협업에 유리합니다.

---

## 6. 심화 개발 가이드라인 (Advanced Rules)

안정적인 JPA 영속성 관리와 데이터 무결성을 위해 모든 개발자는 다음 규칙을 반드시 준수해야 합니다.

### 6.1 Entity 수정 시 변경 감지(Dirty Checking) 활용
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
### 6.2 Entity 내 롬복(Lombok) 어노테이션 사용 제한
- @Data 사용 금지: @Data에는 @Setter와 무분별한 toString(), equals()가 포함되어 있어 JPA 엔티티 간 양방향 연관관계 시 무한 루프나 데이터 오염을 유발합니다.
- @Setter 사용 금지: 객체의 값은 부호 있는 비즈니스 메서드(예: updateName())를 통해서만 변경되어야 하며, 일괄 생성은 @Builder를 활용합니다.
- 기본 생성자 제한: JPA 스펙을 위한 기본 생성자는 외부 생성을 막기 위해 access = AccessLevel.PROTECTED로 제한합니다.

### 6.3 지연 로딩(Lazy Loading) 및 프록시 예외 방지
- Service 레이어에서 DTO 변환 완료: 연관된 엔티티를 함께 조회하여 DTO로 변환해야 하는 경우, 연관 객체에 대한 접근(Getter 호출 및 DTO 카피)은 반드시 트랜잭션(@Transactional)이 살아있는 Service 레이어 내부에서 완료되어야 합니다.
- Controller에서 Lazy 접근 금지: 트랜잭션이 종료된 Controller 레이어에서 연관 엔티티를 DTO로 변환하려고 하면 LazyInitializationException 에러가 발생합니다.
- 트랜잭션 밖에서 Entity 접근 금지

---



## 7. Database & Docker Setup (Local Dev)

이 프로젝트는 `rebuild-engine` 단독으로도 PostgreSQL을 Docker Compose 기반으로 띄울 수 있습니다. 별도 인프라 레포 없이 로컬 DB를 확인하고 싶을 때 사용하는 용도입니다.

### 7.0 활성화 여부

이 기능은 `application.yml`에 값을 넣지 않고 **IntelliJ Run Configuration의 Environment Variables로만 제어**합니다 (공통 설정 파일에 넣으면 운영/개발 모든 환경에 영향을 주기 때문).

- rebuild-infra의 docker-compose로 개발할 때: `SPRING_DOCKER_COMPOSE_ENABLED=false`
- rebuild-engine만 단독 실행할 때: `SPRING_DOCKER_COMPOSE_ENABLED=true` (또는 값을 아예 등록하지 않으면 Spring Boot 기본값이 활성화이므로 자동으로 켜집니다)

> ⚠️ **주의:** 두 compose 파일(`rebuild-infra`의 docker-compose.yml, `rebuild-engine`의 compose.yaml)이 동일한 `container_name: rebuild-postgres`를 사용하기 때문에, 둘을 동시에 띄우면 이름 충돌이 발생합니다. 전환할 때는 반대쪽을 먼저 내려야 합니다.
> - `false`→`true`(단독 실행 전환): rebuild-infra 쪽 Postgres가 떠 있다면 그쪽에서 `docker compose down`
> - `true`→`false`(rebuild-infra로 복귀): rebuild-engine 쪽에서 `docker compose down`으로 단독 컨테이너를 먼저 내린 뒤 rebuild-infra의 docker-compose를 사용

---
### 7.1 Docker Compose
- 파일위치
```shell
rebuild-engine/compose.yaml
```

- compose 설정 (예시 — 실제 값은 하드코딩하지 않고 환경변수로 주입, `rebuild-infra`의 docker-compose.yml과 동일한 구조)
```yaml
services:
  postgres:
    image: postgres:16
    container_name: rebuild-postgres

    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

    ports:
      - "5432:5432"

    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- `build.gradle.kts`에 `org.springframework.boot:spring-boot-docker-compose` 의존성이 포함되어 있어, 활성화 시 Spring Boot가 `compose.yaml`을 자동으로 인식하고 실행/종료합니다.

- 필요한 환경변수 (IntelliJ Run Configuration에 등록)

| 변수명 | 용도 |
| --- | --- |
| `POSTGRES_DB` | 생성할 DB 이름 |
| `POSTGRES_USER` | DB 계정 |
| `POSTGRES_PASSWORD` | DB 비밀번호 |

### 7.2 DB 실행 방법
```shell
cd rebuild-engine
docker compose up -d

docker ps
```


### 7.3 Spring Boot 연결 설정
- `application.yml`은 환경변수만 참조하며, 값을 직접 작성하지 않습니다.
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

### 7.4 전체 실행 순서
```text
1. rebuild-engine 디렉토리 이동
2. docker compose up -d (DB 실행)
3. IntelliJ에서 Spring Boot 실행
4. API 확인 (localhost:9192)
```


### 7.5 DB 초기 설정
```sql
CREATE DATABASE <데이터베이스명>;
```

---
### 7.6 DB 계정 예시
```sql
CREATE USER <계정명> WITH PASSWORD '<비밀번호>';
GRANT ALL PRIVILEGES ON DATABASE rebuild TO <계정명>;

```

---


## 8. 핵심 요약
- Controller → Service → Repository 구조 유지
- Entity는 가장 순수하게 유지
- DTO는 외부 통신 전용
- Service가 모든 흐름 통제