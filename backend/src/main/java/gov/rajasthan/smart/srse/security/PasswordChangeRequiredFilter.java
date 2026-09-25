package gov.rajasthan.smart.srse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks authenticated users with {@code mustChangePassword} except password-change routes.
 */
@Component
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof SessionTokenService.ParsedSessionToken parsed
                && parsed.mustChangePassword()
                && !isPasswordChangeRoute(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"message":"Password change required before using this application."}
                    """);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isPasswordChangeRoute(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/auth/change-password".equals(path) || "/api/auth/login".equals(path);
    }
}
