package cn.gov.document.platform;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentManifestTest {
    @Test
    @SuppressWarnings("unchecked")
    void composeManifestIsValidAndWiresRequiredServices() throws Exception {
        Path manifest = Path.of("..", "compose.yaml").toAbsolutePath().normalize();
        if (!Files.exists(manifest)) manifest = Path.of("compose.yaml").toAbsolutePath().normalize();

        Map<String, Object> root = new Yaml().load(Files.readString(manifest));
        Map<String, Object> services = (Map<String, Object>) root.get("services");

        assertThat(services).containsKeys("postgres", "redis", "minio", "minio-init",
                "oidc-postgres", "oidc", "backend", "web");
        Map<String, Object> minio = (Map<String, Object>) services.get("minio");
        assertThat(minio.get("image").toString()).doesNotContain(":latest");
        Map<String, Object> backend = (Map<String, Object>) services.get("backend");
        Map<String, Object> dependencies = (Map<String, Object>) backend.get("depends_on");
        assertThat(dependencies).containsKeys("postgres", "redis", "minio", "minio-init", "oidc");
        Map<String, Object> environment = (Map<String, Object>) backend.get("environment");
        assertThat(environment).containsEntry("PLATFORM_STORAGE_TYPE", "minio")
                .containsKeys("MINIO_ACCESS_KEY", "MINIO_SECRET_KEY")
                .doesNotContainKeys("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD");

        Map<String, Object> minioInit = (Map<String, Object>) services.get("minio-init");
        Map<String, Object> minioInitEnvironment = (Map<String, Object>) minioInit.get("environment");
        assertThat(minioInitEnvironment).containsKeys("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD",
                "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ciManifestKeepsPostgresClobAndSecurityGatesEnabled() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        if (!Files.exists(root.resolve(".github/workflows/ci.yml"))) root = Path.of(".").toAbsolutePath().normalize();
        Map<String, Object> workflow = new Yaml().load(Files.readString(root.resolve(".github/workflows/ci.yml")));
        Map<String, Object> jobs = (Map<String, Object>) workflow.get("jobs");

        assertThat(jobs).containsKeys("backend", "frontend", "browser-e2e",
                "postgres-compatibility", "container-security");
        assertThat(Files.readString(root.resolve(".github/workflows/ci.yml")))
                .contains("PostgreSqlClobCompatibilityTest")
                .contains("bash -n deploy/ops/*.sh")
                .contains("docker compose --env-file .env.example config --quiet")
                .contains("aquasecurity/trivy-action@v0.36.0")
                .contains("format: cyclonedx");
        assertThat(root.resolve("deploy/ops/backup.sh")).isRegularFile();
        assertThat(root.resolve("deploy/ops/restore.sh")).isRegularFile();
    }
}
