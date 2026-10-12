package gov.rajasthan.smart.srse.security;

import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * Rejects tokens whose {@code sessionVersion} no longer matches the user row (deactivation / password change),
 * and sessions idle for longer than {@code srse.session.idle-timeout} (decision A14).
 *
 * <p>Idle tracking is server-side, per USER, on the row this filter already loads — so it costs no extra
 * read, and one throttled write per active user per {@code activity-write-interval}. Per user, not per
 * session: activity in one browser keeps the user's other concurrent sessions alive. A per-session
 * timeout would need a session table; revisit if that matters.
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class SessionVersionValidationFilter extends OncePerRequestFilter {

    /** Sent on the 401 so the client can say why, instead of a bare "signed out". */
    public static final String REASON_HEADER = "X-Auth-Reason";
    public static final String REASON_IDLE = "idle-timeout";

    private final AppUserRepository userRepository;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    @Autowired
    public SessionVersionValidationFilter(AppUserRepository userRepository, SessionProperties sessionProperties) {
        this(userRepository, sessionProperties, Clock.systemUTC());
    }

    SessionVersionValidationFilter(AppUserRepository userRepository, SessionProperties sessionProperties, Clock clock) {
        this.userRepository = userRepository;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof SessionTokenService.ParsedSessionToken parsed) {
            var user = userRepository.findByUsernameIgnoreCase(parsed.username());
            if (user.isEmpty() || !user.get().isActive()
                    || user.get().getSessionVersion() != parsed.sessionVersion()) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            if (sessionProperties.idleTimeoutEnabled()) {
                AppUser current = user.get();
                Instant now = clock.instant();
                Instant last = current.getLastActivityAt();
                if (last != null && now.isAfter(last.plus(sessionProperties.idleTimeout()))) {
                    SecurityContextHolder.clearContext();
                    response.setHeader(REASON_HEADER, REASON_IDLE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                // null = a session that predates this column: treat as active and stamp it.
                if (last == null || last.isBefore(now.minus(sessionProperties.activityWriteInterval()))) {
                    userRepository.touchActivity(
                            current.getId(), now, now.minus(sessionProperties.activityWriteInterval()));
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
