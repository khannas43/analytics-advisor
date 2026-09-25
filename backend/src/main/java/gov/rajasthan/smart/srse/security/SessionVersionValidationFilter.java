package gov.rajasthan.smart.srse.security;

import gov.rajasthan.smart.srse.identity.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects tokens whose {@code sessionVersion} no longer matches the user row (deactivation / password change).
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class SessionVersionValidationFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;

    public SessionVersionValidationFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
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
        }
        filterChain.doFilter(request, response);
    }
}
