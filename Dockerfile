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

# 시뮬레이터 시드 데이터(합성 테스트 데이터). WORKDIR(/app) 기준 simulator.seed.csv-path 기본값 seed.csv 와 매칭.
COPY seed.csv .

EXPOSE 8095

ENTRYPOINT ["java", "-jar", "app.jar"]
