package cn.gov.document.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DeploymentManifestTest {
    @Test
    @SuppressWarnings("unchecked")
    void composeManifestIsValidAndWiresRequiredServices() throws Exception {
        Path manifest = Path.of("..", "compose.yaml").toAbsolutePath().normalize();
        if (!Files.exists(manifest))
            manifest = Path.of("compose.yaml").toAbsolutePath().normalize();

        Map<String, Object> root = new Yaml().load(Files.readString(manifest));
        Map<String, Object> services = (Map<String, Object>) root.get("services");

        assertThat(services)
                .containsKeys("postgres", "redis", "minio", "minio-init", "oidc-postgres", "oidc", "backend", "web");
        Map<String, Object> minio = (Map<String, Object>) services.get("minio");
        assertThat(minio.get("image").toString()).doesNotContain(":latest");
        Map<String, Object> backend = (Map<String, Object>) services.get("backend");
        Map<String, Object> dependencies = (Map<String, Object>) backend.get("depends_on");
        assertThat(dependencies).containsKeys("postgres", "redis", "minio", "minio-init", "oidc");
        Map<String, Object> environment = (Map<String, Object>) backend.get("environment");
        assertThat(environment)
                .containsEntry("PLATFORM_STORAGE_TYPE", "minio")
                .containsKeys("MINIO_ACCESS_KEY", "MINIO_SECRET_KEY")
                .doesNotContainKeys("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD");

        // 定稿稿在线编辑默认启用：文档服务器固定版本、启用 JWT，后端与前端同源代理均已接线
        Map<String, Object> onlyOffice = (Map<String, Object>) services.get("onlyoffice");
        assertThat(onlyOffice.get("image").toString()).startsWith("onlyoffice/documentserver:").doesNotContain(":latest");
        assertThat((Map<String, Object>) onlyOffice.get("environment")).containsEntry("JWT_ENABLED", "true");
        assertThat(environment)
                .containsKeys("PLATFORM_ONLYOFFICE_ENABLED", "PLATFORM_ONLYOFFICE_JWT_SECRET")
                .containsEntry("PLATFORM_ONLYOFFICE_INTERNAL_URL", "http://onlyoffice");
        Map<String, Object> web = (Map<String, Object>) services.get("web");
        assertThat((Map<String, Object>) web.get("depends_on")).containsKey("onlyoffice");
        Path nginx = manifest.getParent().resolve("platform-web/nginx.conf");
        assertThat(Files.readString(nginx))
                .contains("location /office/", "proxy_pass http://onlyoffice/", "X-Frame-Options SAMEORIGIN");

        Map<String, Object> minioInit = (Map<String, Object>) services.get("minio-init");
        Map<String, Object> minioInitEnvironment = (Map<String, Object>) minioInit.get("environment");
        assertThat(minioInitEnvironment)
                .containsKeys("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ciManifestKeepsPostgresClobAndSecurityGatesEnabled() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        if (!Files.exists(root.resolve(".github/workflows/ci.yml")))
            root = Path.of(".").toAbsolutePath().normalize();
        Map<String, Object> workflow = new Yaml().load(Files.readString(root.resolve(".github/workflows/ci.yml")));
        Map<String, Object> jobs = (Map<String, Object>) workflow.get("jobs");

        assertThat(jobs)
                .containsKeys("backend", "frontend", "browser-e2e", "postgres-compatibility", "container-security");
        assertThat(Files.readString(root.resolve(".github/workflows/ci.yml")))
                .contains("PostgreSqlClobCompatibilityTest")
                .contains("bash -n deploy/ops/*.sh")
                .contains("docker compose --env-file .env.example config --quiet")
                .contains("aquasecurity/trivy-action@v0.36.0")
                .contains("format: cyclonedx");
        assertThat(root.resolve("deploy/ops/backup.sh")).isRegularFile();
        assertThat(root.resolve("deploy/ops/restore.sh")).isRegularFile();
    }

    @Test
    void projectPortsAvoidReservedLocalServices() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        if (!Files.exists(root.resolve("compose.yaml")))
            root = Path.of(".").toAbsolutePath().normalize();
        String reservedBackend = "80" + "80";
        String reservedFrontendA = "51" + "73";
        String reservedFrontendB = "51" + "74";

        assertThat(Files.readString(root.resolve("platform-bootstrap/src/main/resources/application.yml")))
                .contains("port: ${SERVER_PORT:18089}")
                .contains("http://localhost:5175")
                .doesNotContain(
                        "${SERVER_PORT:" + reservedBackend + "}",
                        "localhost:" + reservedFrontendA,
                        "localhost:" + reservedFrontendB);
        assertThat(Files.readString(root.resolve("platform-web/vite.config.ts")))
                .contains("'http://localhost:18089'", "port: 5175", "strictPort: true")
                .doesNotContain(
                        "localhost:" + reservedBackend, "port: " + reservedFrontendA, "port: " + reservedFrontendB);
        assertThat(Files.readString(root.resolve("compose.yaml")))
                .contains("127.0.0.1:8180:8180", "http://oidc:8180", "127.0.0.1:18089")
                .doesNotContain(":" + reservedBackend);
        assertThat(Files.readString(root.resolve("platform-web/nginx.conf")))
                .contains("backend:18089")
                .doesNotContain("backend:" + reservedBackend);
        assertThat(Files.readString(root.resolve("Dockerfile")))
                .contains("EXPOSE 18089")
                .doesNotContain("EXPOSE " + reservedBackend);
    }
}
