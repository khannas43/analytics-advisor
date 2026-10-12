package gov.rajasthan.smart.srse.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local accounts are real accounts, so their session tokens must outlive a restart and validate on
 * every instance. Refusing to start is deliberate — the same stance as the bootstrap password
 * (7.1b.2): fail loudly rather than run with sessions that quietly die on every deploy.
 * {@code mock} mode keeps the random per-boot key; it is a laptop convenience, not an ACL.
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class SessionSigningKeyStartupValidator implements InitializingBean {

    private final SessionTokenService tokens;

    public SessionSigningKeyStartupValidator(SessionTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public void afterPropertiesSet() {
        if (!tokens.signingKeyConfigured()) {
            throw new IllegalStateException(
                    "srse.auth-mode=local requires SRSE_SESSION_SIGNING_KEY (Base64, at least 32 bytes). "
                            + "Generate one with: openssl rand -base64 48 "
                            + "(scripts/deploy-docker.sh does this for you). Changing it signs everyone out.");
        }
    }
}
