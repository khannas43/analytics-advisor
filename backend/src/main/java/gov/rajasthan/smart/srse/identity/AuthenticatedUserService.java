package gov.rajasthan.smart.srse.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    private final AppUserRepository userRepository;

    public AuthenticatedUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AdminAccessDeniedException("Not authenticated");
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AdminAccessDeniedException("Unknown user"));
    }
}
