package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.security.Authorities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAuthorityMapperAuditTest {

    @Test
    void superAdminGetsAuditReadAdminDoesNot() {
        List<String> superAuth = RoleAuthorityMapper.toAuthorities(List.of(AppRole.SUPER_ADMIN));
        assertTrue(superAuth.contains(Authorities.AUDIT_READ));

        List<String> adminAuth = RoleAuthorityMapper.toAuthorities(List.of(AppRole.ADMIN));
        assertFalse(adminAuth.contains(Authorities.AUDIT_READ));
    }

    @Test
    void auditReaderRoleMapsToAuditReadOnly() {
        List<String> auth = RoleAuthorityMapper.toAuthorities(List.of(AppRole.AUDIT_READER));
        assertTrue(auth.contains(Authorities.AUDIT_READ));
        assertFalse(auth.contains(Authorities.SRSE_ADMIN));
    }
}
