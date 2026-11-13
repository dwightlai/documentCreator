package cn.gov.document.platform.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalAdminPasswordBootstrap implements ApplicationRunner {
    private final AuthSessionService sessions;
    private final String password;

    public LocalAdminPasswordBootstrap(
            AuthSessionService sessions, @Value("${platform.security.local-admin-password:admin123}") String password) {
        this.sessions = sessions;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        sessions.ensureSeedPassword(password);
    }
}
