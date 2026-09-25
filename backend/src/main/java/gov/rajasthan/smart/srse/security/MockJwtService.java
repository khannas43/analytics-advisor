package gov.rajasthan.smart.srse.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock-token helper for {@link MockJwtIssuer}. Delegates to {@link SessionTokenService}.
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "mock", matchIfMissing = true)
public class MockJwtService {

    private final SessionTokenService sessionTokenService;

    public MockJwtService(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    public String issue(String subject) {
        return issue(subject, List.of(Authorities.STATE_OFFICER));
    }

    public String issue(String subject, List<String> authorities) {
        return sessionTokenService.issue(subject, authorities, 0L, false);
    }

    public List<String> parseAuthorities(String token) {
        return sessionTokenService.parse(token).authorities();
    }
}
