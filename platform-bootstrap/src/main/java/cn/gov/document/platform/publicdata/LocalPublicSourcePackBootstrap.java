package cn.gov.document.platform.publicdata;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalPublicSourcePackBootstrap implements ApplicationRunner {
    private final PublicSourcePackService service;

    public LocalPublicSourcePackBootstrap(PublicSourcePackService service) { this.service = service; }

    @Override
    public void run(ApplicationArguments args) {
        service.install("local-government", "public-source-pack");
    }
}
