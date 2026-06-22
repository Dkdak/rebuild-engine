# Rebuild Project Architecture (확정 구조 정리)

## 1. 전체 목표

리모델링 투자 분석 시스템을 아래 구조로 설계한다:

- 분석 엔진 (Spring Boot)
- 사용자 UI (React)
- 실행/배포 인프라 (Docker + DB + CI/CD)

---

## 2. Git 구조 (멀티 레포)

각 서비스는 독립 Git 저장소로 관리한다.

```css
rebuild-engine (Spring Boot API / AI / 분석 로직)
rebuild-channel (React UI / 사용자 화면)
rebuild-infra (Docker / DB / 배포 / CI/CD)
```

---

## 3. 로컬 개발 구조

각 레포는 독립적으로 개발하고 실행한다.

```css
engine → IntelliJ (Spring Boot 실행)
channel → VSCode (React 실행)
infra → docker-compose 실행
```

---

## 4. 핵심 연결 구조

서비스 간 통신 방식은 다음과 같다:

```css
channel (React)
↓ HTTP API
engine (Spring Boot)
↓
PostgreSQL (DB container)
```

---

## 5. infra 역할 (핵심 레버)

infra는 전체 시스템 실행을 통제한다.

```css
rebuild-project/
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
│
├── rebuild-channel/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── public/
│   └── src/
│
├── rebuild-infra/
│   ├── docker-compose.yml
│   │
│   ├── postgres/
│   │   ├── init/
│   │   │   ├── init.sh
│   │   │   └── init.sql
│   │   └── Dockerfile (선택)
│   │
│   └── nginx/ (선택)
│
└── README.md
```

### infra 역할
- 전체 서비스 실행 제어
- DB(PostgreSQL) 포함
- 배포 자동화
- EC2 실행 환경 구성

---

## 6. docker-compose 역할

docker-compose는 전체 시스템을 하나로 묶는 실행 스위치이다.


services:
engine
channel
postgres


→ infra 안에서 관리됨

---

## 7. 로컬 실행 방식

개발자는 각자 독립적으로 실행한다.

- engine: Spring Boot 실행
- channel: npm run dev
- infra: docker-compose up

---

## 8. 배포 구조 (CI/CD + EC2)

GitHub Actions 기반 배포 흐름:


Git Push
↓
GitHub Actions (build)
↓
EC2 서버 배포
↓
docker-compose up


---

## 9. EC2 실행 구조

서버에서는 전체 시스템이 docker로 실행된다.

```css
/app
├── engine container
├── channel container
├── postgres container
└── docker-compose.yml
```



---

## 10. 핵심 설계 원칙

### ✔ 개발 구조
- engine / channel 완전 분리

### ✔ 운영 구조
- infra로 전체 통합 제어

### ✔ 데이터 구조
- engine만 DB 접근
- channel은 API만 사용

### ✔ 실행 구조
- docker-compose가 전체 시스템 통제

---

## 11. 최종 핵심 요약

- Git은 3개로 분리 (engine / channel / infra)
- 로컬 개발은 각자 독립 실행
- 연결은 HTTP API 기반
- DB는 infra의 PostgreSQL container
- 배포는 GitHub Actions → EC2 → docker-compose
- 전체 시스템은 infra가 레버 역할