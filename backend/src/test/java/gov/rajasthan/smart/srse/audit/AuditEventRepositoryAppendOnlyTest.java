package gov.rajasthan.smart.srse.audit;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7.3 A9 — there must be no way to delete or amend an audit row.
 *
 * <p>An earlier version of this test inspected only
 * {@code getDeclaredMethods()} and then asserted the repository extended
 * {@code JpaRepository}. Both halves were wrong together: declared methods are
 * empty on a Spring Data interface because everything is inherited, and
 * {@code JpaRepository} is precisely what inherits {@code deleteAll}. The test
 * passed while any caller could erase the log, and it pinned in place the
 * inheritance that made that possible.
 *
 * <p>So this walks the <b>whole</b> method surface, inherited included.
 */
class AuditEventRepositoryAppendOnlyTest {

    private static final Set<String> FORBIDDEN = Set.of("delete", "remove", "update", "truncate");

    @Test
    void noMutatorIsReachableOnTheAuditRepository() {
        for (Method method : AuditEventRepository.class.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertTrue(FORBIDDEN.stream().noneMatch(name::contains),
                    () -> "Reachable mutator on the audit repository: " + method.getName()
                            + " — audit rows must never be removable or amendable");
        }
    }

    @Test
    void theRepositoryDoesNotInheritTheBroadCrudSurface() {
        assertFalse(JpaRepository.class.isAssignableFrom(AuditEventRepository.class),
                "extending JpaRepository re-introduces deleteAll and friends by inheritance, "
                        + "however carefully the interface itself is written");
    }
}
