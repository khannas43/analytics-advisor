package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.identity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordUserContactChange(AppUser actor, AppUser target, String field, String detail) {
        repository.save(new AuditEvent(
                Instant.now(),
                actor == null ? null : actor.getId(),
                target.getId(),
                "USER_CONTACT_" + field,
                detail));
    }
}
