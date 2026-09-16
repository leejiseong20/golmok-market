# ============================================================
# 골목마켓 백엔드 이미지
#
# 멀티스테이지: 빌드 도구(JDK·Gradle)는 최종 이미지에 넣지 않는다.
# 최종 이미지는 JRE 와 jar 만 담아 크기와 공격 표면을 줄인다.
#
# 빌드는 GitHub Actions 에서 한다(.github/workflows/docker-image.yml).
# 배포 서버(EC2 t3.micro, 메모리 1GB)에서 Gradle 빌드를 돌리면 메모리가 부족해 매우 느리거나 실패한다.
# ============================================================

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 목록이 바뀌지 않으면 이 레이어를 캐시에서 재사용한다. 소스만 고쳤을 때 재다운로드를 피한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 CI 에서 별도 단계로 돌린다. 이미지 빌드에서 반복하지 않는다.
RUN ./gradlew bootJar --no-daemon -x test


FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 실행하지 않는다. 컨테이너가 뚫려도 호스트 권한으로 이어지는 범위를 줄인다.
RUN useradd --system --uid 1001 golmok \
    && mkdir -p /data/uploads \
    && chown golmok:golmok /data/uploads

COPY --from=build /workspace/build/libs/golmok-market.jar app.jar

USER golmok

# 메모리 1GB 서버에서 MySQL 과 함께 돌리므로 힙을 명시적으로 제한한다.
# 제한하지 않으면 JVM 이 서버 메모리 비율로 힙을 잡아 MySQL 과 경쟁하다 OOM 으로 죽을 수 있다.
# SerialGC: 코어 1~2개·작은 힙에서는 병렬 GC 의 스레드 비용이 이득보다 크다.
# 힙만 제한하면 부족하다. 실측에서 힙 320MB 제한인데 프로세스는 460MB 를 썼다.
# 힙 밖(JIT 코드 캐시·다이렉트 버퍼·스레드 스택)도 함께 줄인다.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx300m -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Duser.timezone=Asia/Seoul"
# 톰캣 기본 작업 스레드는 200개다. 스레드마다 스택 메모리를 쓰므로 시연 규모에 맞게 줄인다.
ENV SERVER_TOMCAT_THREADS_MAX=40
ENV IMAGE_UPLOAD_DIR=/data/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
