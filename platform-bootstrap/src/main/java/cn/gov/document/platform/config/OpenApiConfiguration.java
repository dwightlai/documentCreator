package cn.gov.document.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI platformOpenApi() {
        return new OpenAPI().info(new Info()
            .title("智能公文写作与政务智能审校平台 API")
            .version("v1")
            .description("V1.6 详细设计对应的 REST 与流式接口契约")
            .license(new License().name("Proprietary")));
    }
}
