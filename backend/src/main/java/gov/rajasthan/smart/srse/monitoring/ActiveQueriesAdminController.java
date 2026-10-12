package gov.rajasthan.smart.srse.monitoring;

import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin view of analytical work started on <strong>this JVM</strong> only.
 * Not audited (A7 metadata browsing). Never includes SQL, bound values, or query shape.
 */
@RestController
@RequestMapping("/api/admin/queries")
public class ActiveQueriesAdminController {

    private final QueryExecutionMonitor monitor;
    private final AdminAuthorizationService authorization;
    private final AuthenticatedUserService users;

    public ActiveQueriesAdminController(
            QueryExecutionMonitor monitor,
            AdminAuthorizationService authorization,
            AuthenticatedUserService users) {
        this.monitor = monitor;
        this.authorization = authorization;
        this.users = users;
    }

    @GetMapping("/active")
    public ActiveQueriesResponse active() {
        requireSuperAdmin();
        ActiveQueryRegistry.ActiveQueriesSnapshot snapshot = monitor.snapshot();
        return new ActiveQueriesResponse(
                "This list covers only queries started on this backend instance.",
                snapshot.running(),
                snapshot.recent());
    }

    private void requireSuperAdmin() {
        AppUser actor = users.requireCurrentUser();
        authorization.assertSuperAdminOnly(actor);
    }

    public record ActiveQueriesResponse(
            String instanceNote,
            java.util.List<ActiveQueryRegistry.ActiveQueryView> running,
            java.util.List<ActiveQueryRegistry.ActiveQueryView> recent) {
    }
}
