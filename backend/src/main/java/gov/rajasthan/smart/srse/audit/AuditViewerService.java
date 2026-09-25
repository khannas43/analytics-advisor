package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.audit.admin.AuditEventView;
import gov.rajasthan.smart.srse.audit.admin.AuditPageResponse;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Scoped audit log reads — one query path for viewer and CSV export (§7.3.4 / 7.3.5). */
@Service
public class AuditViewerService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 200_000;

    private final AuditEventQueryDao queryDao;
    private final AppUserRepository userRepository;
    private final AdminAuthorizationService authorization;
    private final AuditProperties auditProperties;

    public AuditViewerService(
            AuditEventQueryDao queryDao,
            AppUserRepository userRepository,
            AdminAuthorizationService authorization,
            AuditProperties auditProperties) {
        this.queryDao = queryDao;
        this.userRepository = userRepository;
        this.authorization = authorization;
        this.auditProperties = auditProperties;
    }

    @Transactional(readOnly = true)
    public AuditPageResponse search(
            AppUser reader,
            Instant from,
            Instant to,
            Long actorUserId,
            AuditActionType actionType,
            AuditOutcome outcome,
            int page,
            int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        AuditSearchCriteria criteria = buildCriteria(reader, from, to, actorUserId, actionType, outcome);
        Page<AuditEvent> result = queryDao.findPage(
                criteria,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        Map<Long, String> usernames = resolveUsernames(result.getContent());
        List<AuditEventView> views = result.getContent().stream()
                .map(e -> AuditEventView.from(e, usernames.get(e.getActorUserId())))
                .toList();
        return new AuditPageResponse(
                views,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                auditProperties.retentionDays(),
                "Retention is reported only — no automatic purge runs in this release.");
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> searchForExport(
            AppUser reader,
            Instant from,
            Instant to,
            Long actorUserId,
            AuditActionType actionType,
            AuditOutcome outcome) {
        AuditSearchCriteria criteria = buildCriteria(reader, from, to, actorUserId, actionType, outcome);
        List<AuditEvent> rows = queryDao.findAll(criteria, MAX_EXPORT_ROWS);
        Map<Long, String> usernames = resolveUsernames(rows);
        return rows.stream()
                .map(e -> AuditEventView.from(e, usernames.get(e.getActorUserId())))
                .toList();
    }

    AuditSearchCriteria buildCriteria(
            AppUser reader,
            Instant from,
            Instant to,
            Long actorUserId,
            AuditActionType actionType,
            AuditOutcome outcome) {
        boolean superAdmin = authorization.isSuperAdmin(reader);
        Set<Long> visibleActors = superAdmin ? null : visibleActiveActorIds(reader);
        if (!superAdmin && actorUserId != null && (visibleActors == null || !visibleActors.contains(actorUserId))) {
            visibleActors = Set.of();
        }
        return new AuditSearchCriteria(from, to, actorUserId, actionType, outcome, visibleActors, superAdmin);
    }

    /**
     * §7.3.8 — reuses {@link AdminAuthorizationService#canManageUser} (rule 2). Inactive
     * users are excluded so archived actors stay SuperAdmin-only at read time.
     */
    Set<Long> visibleActiveActorIds(AppUser reader) {
        Set<Long> ids = new HashSet<>();
        for (AppUser user : userRepository.findAllByOrderByUsernameAsc()) {
            if (!user.isActive()) {
                continue;
            }
            if (authorization.readerMaySeeActor(reader, user)) {
                ids.add(user.getId());
            }
        }
        return ids;
    }

    private Map<Long, String> resolveUsernames(List<AuditEvent> events) {
        Set<Long> ids = events.stream()
                .map(AuditEvent::getActorUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        for (AppUser user : userRepository.findAllById(ids)) {
            out.put(user.getId(), user.getUsername());
        }
        return out;
    }
}
