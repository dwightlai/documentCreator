FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY platform-common/pom.xml platform-common/pom.xml
COPY platform-writing/pom.xml platform-writing/pom.xml
COPY platform-document/pom.xml platform-document/pom.xml
COPY platform-review/pom.xml platform-review/pom.xml
COPY platform-rule/pom.xml platform-rule/pom.xml
COPY platform-knowledge/pom.xml platform-knowledge/pom.xml
COPY platform-model-gateway/pom.xml platform-model-gateway/pom.xml
COPY platform-integration/pom.xml platform-integration/pom.xml
COPY platform-job/pom.xml platform-job/pom.xml
COPY platform-bootstrap/pom.xml platform-bootstrap/pom.xml
RUN mvn -B -ntp dependency:go-offline -DskipTests
COPY . .
RUN mvn -B -ntp -pl platform-bootstrap -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl fonts-noto-cjk fontconfig && rm -rf /var/lib/apt/lists/*
RUN groupadd --system platform && useradd --system --gid platform --home-dir /app platform
WORKDIR /app
COPY --from=build /workspace/platform-bootstrap/target/platform-bootstrap-*.jar app.jar
RUN chown platform:platform app.jar
USER platform
EXPOSE 18080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
