package gov.rajasthan.smart.srse.scope;

import gov.rajasthan.smart.srse.identity.UserScopeAssignment;
import gov.rajasthan.smart.srse.identity.UserScopeAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Loads user scope assignments. §7.2 will translate
 * {@link #assignmentPathsByDimension(long)} into SQL predicates; this service
 * does not inject filters into any query.
 *
 * <h2>Assignment semantics (fixed for §7.2 — do not change without updating tests)</h2>
 * <ul>
 *   <li><strong>AND across dimensions, OR within one.</strong> Assignments in
 *       geography and department combine with AND; two nodes in the same dimension
 *       combine with OR (Jaipur OR Alwar AND Health).</li>
 *   <li><strong>Subtree (A3).</strong> An assigned node covers itself and every
 *       descendant, using {@code scope_node.path} prefix matching — not a recursive
 *       walk.</li>
 *   <li><strong>Absent dimension = no access.</strong> No {@code user_scope_assignment}
 *       row in a dimension means the user has no access in that dimension, not unrestricted
 *       access. Adding a new dimension therefore denies everyone until an admin grants it.</li>
 *   <li><strong>Whole dimension via root.</strong> Assign the dimension's root node
 *       (exactly one per dimension) to grant access to the entire tree under that axis.</li>
 * </ul>
 */
@Service
public class UserScopeAssignmentService {

    private final UserScopeAssignmentRepository assignmentRepository;

    public UserScopeAssignmentService(UserScopeAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public Map<Long, List<String>> assignmentPathsByDimension(long userId) {
        List<UserScopeAssignment> assignments = assignmentRepository.findAllWithNodeByUserId(userId);
        List<ScopeAccessEvaluator.ScopedAssignment> scoped = assignments.stream()
                .map(a -> new ScopeAccessEvaluator.ScopedAssignment(
                        a.getScopeNode().getDimension().getId(),
                        a.getScopeNode().getPath()))
                .toList();
        return ScopeAccessEvaluator.groupAssignmentPathsByDimension(scoped);
    }
}
