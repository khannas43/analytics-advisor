package gov.rajasthan.smart.srse.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the client IP for audit rows (§7.3). Honours {@code X-Forwarded-For}
 * only when the immediate peer is a configured trusted proxy; empty list = never
 * trust the header (safe default on a laptop). Set {@code srse.audit.trusted-proxies}
 * behind a load balancer.
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(AuditProperties properties) {
        this.trustedProxies = new HashSet<>(properties.trustedProxies());
    }

    public String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return resolveClientIp(attrs.getRequest());
    }

    String resolveClientIp(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || trustedProxies.isEmpty()) {
            return remote;
        }
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        List<String> chain = Arrays.stream(forwarded.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalize)
                .toList();
        for (int i = chain.size() - 1; i >= 0; i--) {
            String hop = chain.get(i);
            if (!trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remote;
    }

    private String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        if (ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }
        return ip.trim();
    }
}
