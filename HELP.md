# 시작하기 (Getting Started)

### 참고 문서 (Reference Documentation)

추가적인 참고 자료는 아래 문서를 확인하세요.:

* Gradle 공식 문서 [Official Gradle documentation](https://docs.gradle.org)
* Spring Boot Gradle Plugin 참고 가이드 [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.0/gradle-plugin)
* OCI 이미지 생성 방법 [Create an OCI image](https://docs.spring.io/spring-boot/4.1.0/gradle-plugin/packaging-oci-image.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.0/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.0/reference/using/devtools.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.1.0/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.0/reference/web/servlet.html)

### 가이드 (Guides)

다음 가이드들은 주요 기능을 실제로 사용하는 방법을 설명합니다.:

* JPA를 이용한 데이터 접근 [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* RESTful 웹 서비스 구축 [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* Spring MVC를 이용한 웹 콘텐츠 제공 [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* Spring 기반 REST 서비스 개발 [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### 추가 링크 (Additional Links)

프로젝트 빌드와 관련된 추가 정보는 아래 링크를 참고하세요.:

* Gradle Build Scans – 빌드 분석 및 성능 확인 [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

### Docker Compose 지원 (Docker Compose Support)

이 프로젝트에는 compose.yaml 파일이 포함되어 있습니다.
해당 파일에는 다음 서비스가 정의되어 있습니다.:

* postgres: [`postgres:latest`](https://hub.docker.com/_/postgres)

프로덕션 환경과 동일한 버전을 사용하도록 Docker 이미지 태그를 검토하고 수정하는 것을 권장합니다.





# Rebuild Engine API

리모델링 투자 수익성 분석 엔진 백엔드

## 기술 스택

- Java 21
- Spring Boot 4
- Spring Data JPA
- PostgreSQL
- Docker Compose
- Gradle

## 실행 방법

### 1. PostgreSQL 실행

```bash
docker compose up -d

```

### 2. Spring Boot 실행

```bash
./gradlew bootRun

```



### 3. API 확인
```bash
http://localhost:8080/api/health

```


### 주요 기능
- 건축물 조회
- 리모델링 대상 선별
- 공시가격 조회
- 예상 공사비 계산
- 수익성 분석

지금 단계에서는 README보다 
먼저 `/api/health` 와 `/api/buildings` API부터 만드는 것이 좋다. 
그러면 React 화면과 바로 연동할 수 있다.
