package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.security.Authorities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps product role codes to Spring Security authorities used by {@code SecurityConfig}. */
public final class RoleAuthorityMapper {

    private RoleAuthorityMapper() {
    }

    public static List<String> toAuthorities(Collection<String> roleCodes) {
        Set<String> authorities = new LinkedHashSet<>();
        for (String code : roleCodes) {
            switch (code) {
                case AppRole.SUPER_ADMIN, AppRole.ADMIN -> {
                    authorities.add(Authorities.SRSE_ADMIN);
                    authorities.add(Authorities.STATE_OFFICER);
                }
                case AppRole.OFFICER -> authorities.add(Authorities.STATE_OFFICER);
                default -> {
                    // unknown role — ignore
                }
            }
        }
        return new ArrayList<>(authorities);
    }
}
