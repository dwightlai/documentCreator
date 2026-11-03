package cn.gov.document.platform.standards;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalOfficialReviewPackBootstrap implements ApplicationRunner {
    private final OfficialReviewPackService service;

    public LocalOfficialReviewPackBootstrap(OfficialReviewPackService service) { this.service = service; }

    @Override
    public void run(ApplicationArguments args) {
        service.install("local-government", "official-pack");
    }
}
