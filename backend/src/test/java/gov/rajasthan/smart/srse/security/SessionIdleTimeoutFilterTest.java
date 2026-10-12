package gov.rajasthan.smart.srse.security;

import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** PRODUCT_PLAN 10.B.2 — the 30-minute idle timeout (decision A14). */
@ExtendWith(MockitoExtension.class)
class SessionIdleTimeoutFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    @Mock
    private AppUserRepository users;
    @Mock
    private AppUser user;

    private SessionVersionValidationFilter filter;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SessionProperties policy = new SessionProperties(
                Duration.ofHours(8), Duration.ofMinutes(30), Duration.ofMinutes(1));
        filter = new SessionVersionValidationFilter(users, policy, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(users.findByUsernameIgnoreCase("officer")).thenReturn(Optional.of(user));
        lenient().when(user.isActive()).thenReturn(true);
        lenient().when(user.getSessionVersion()).thenReturn(3L);
        lenient().when(user.getId()).thenReturn(9L);
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        var auth = new UsernamePasswordAuthenticationToken("officer", null, List.of());
        auth.setDetails(new SessionTokenService.ParsedSessionToken("officer", List.of(), 3L, false));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void run() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), response, chain);
    }

    @Test
    void idleBeyondTheTimeoutIsRefusedWithAReasonAndNotStamped() throws Exception {
        lenient().when(user.getLastActivityAt()).thenReturn(NOW.minus(Duration.ofMinutes(31)));

        run();

        assertEquals(401, response.getStatus());
        assertEquals("idle-timeout", response.getHeader("X-Auth-Reason"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        // An expired session must not refresh itself by being refused.
        verify(users, never()).touchActivity(anyLong(), any(), any());
    }

    @Test
    void justInsideTheTimeoutPassesAndRefreshesTheStamp() throws Exception {
        lenient().when(user.getLastActivityAt()).thenReturn(NOW.minus(Duration.ofMinutes(29)));

        run();

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        verify(users).touchActivity(9L, NOW, NOW.minus(Duration.ofMinutes(1)));
    }

    @Test
    void recentActivityDoesNotCauseAWrite() throws Exception {
        lenient().when(user.getLastActivityAt()).thenReturn(NOW.minus(Duration.ofSeconds(20)));

        run();

        verify(chain).doFilter(any(), any());
        verify(users, never()).touchActivity(anyLong(), any(), any());
    }

    @Test
    void aSessionThatPredatesTheColumnIsTreatedAsActiveAndStamped() throws Exception {
        lenient().when(user.getLastActivityAt()).thenReturn(null);

        run();

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        verify(users).touchActivity(9L, NOW, NOW.minus(Duration.ofMinutes(1)));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void zeroTimeoutDisablesTheCheckEntirely() throws Exception {
        SessionProperties off = new SessionProperties(null, Duration.ZERO, null);
        filter = new SessionVersionValidationFilter(users, off, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(user.getLastActivityAt()).thenReturn(NOW.minus(Duration.ofDays(3)));

        run();

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        verify(users, never()).touchActivity(anyLong(), any(), any());
    }

    @Test
    void aStaleVersionIsStillRefusedFirst() throws Exception {
        lenient().when(user.getSessionVersion()).thenReturn(4L);
        lenient().when(user.getLastActivityAt()).thenReturn(NOW);

        run();

        assertEquals(401, response.getStatus());
        assertNull(response.getHeader("X-Auth-Reason"));
    }
}
