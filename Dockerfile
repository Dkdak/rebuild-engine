# Stage 1: Build (내장 gradle-wrapper 사용하도록 변경)
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# 1. 가볍게 가이드 파일 및 wrapper 설정 파일들만 복사
COPY gradle /app/gradle
COPY gradlew build.gradle* settings.gradle* /app/

# 2. Linux 환경에 맞게 gradlew 실행 권한 부여 및 의존성 다운로드
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# 3. 소스 코드 전체 복사 후 내장 gradlew로 빌드
COPY . /app/
RUN chmod +x ./gradlew
RUN ./gradlew build -x test --parallel --no-daemon

# Stage 2: Runtime (기존 유지)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*SNAPSHOT.jar /app/app.jar
EXPOSE 9192
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]