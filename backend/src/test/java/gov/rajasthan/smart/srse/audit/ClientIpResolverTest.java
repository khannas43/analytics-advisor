package gov.rajasthan.smart.srse.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedForWhenNoTrustedProxies() {
        ClientIpResolver resolver = new ClientIpResolver(new AuditProperties(List.of()));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");

        assertEquals("203.0.113.10", resolver.resolveClientIp(request));
    }

    @Test
    void ignoresForwardedForWhenPeerIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(new AuditProperties(List.of("10.0.0.1")));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99");

        assertEquals("203.0.113.10", resolver.resolveClientIp(request));
    }

    @Test
    void honoursForwardedForFromTrustedPeerRightmostUntrusted() {
        ClientIpResolver resolver = new ClientIpResolver(new AuditProperties(List.of("10.0.0.1", "198.51.100.2")));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.55, 198.51.100.2, 10.0.0.5");

        assertEquals("10.0.0.5", resolver.resolveClientIp(request));
    }
}
