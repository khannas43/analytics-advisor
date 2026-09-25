package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import gov.rajasthan.smart.srse.lakehouse.OfficerRegistryScopeService;
import gov.rajasthan.smart.srse.lakehouse.TableScopePolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditScopeSummaryService {

    private final OfficerRegistryScopeService officerScope;
    private final AppUserRepository userRepository;

    public AuditScopeSummaryService(
            OfficerRegistryScopeService officerScope,
            AppUserRepository userRepository) {
        this.officerScope = officerScope;
        this.userRepository = userRepository;
    }

    public String summarizeCurrentOfficer() {
        TableScopePolicy.OfficerScopeView view = officerScope.currentOfficerScope();
        if (view.bypassDataScoping()) {
            return "BYPASS";
        }
        Map<Long, java.util.List<TableScopePolicy.AssignmentNode>> assignments = view.assignmentsByDimension();
        if (assignments == null || assignments.isEmpty()) {
            return "DENY";
        }
        return assignments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "d" + e.getKey() + ":" + e.getValue().stream()
                        .map(TableScopePolicy.AssignmentNode::path)
                        .sorted()
                        .collect(Collectors.joining("|")))
                .collect(Collectors.joining(";"));
    }

    public Long currentActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(AppUser::getId)
                .orElse(null);
    }
}
