FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# 시뮬레이터 시드 데이터(합성 테스트 데이터). WORKDIR(/app) 기준 기본 경로와 매칭.
COPY seed.csv .
# 결제이력 시더 입력 (extract_tier_thresholds.py 산출물). tier 임계값 매핑.
COPY tier-thresholds.json .

EXPOSE 8095

ENTRYPOINT ["java", "-jar", "app.jar"]
