package gov.rajasthan.smart.srse.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** Paged, filtered reads for {@link AuditEvent} — append-only repository stays insert-only (§7.3 A9). */
@Repository
public class AuditEventQueryDao {

    private final EntityManager entityManager;

    public AuditEventQueryDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<AuditEvent> findPage(AuditSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEvent> query = cb.createQuery(AuditEvent.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        Predicate predicate = buildPredicate(cb, root, criteria);
        query.where(predicate);
        query.orderBy(toOrders(cb, root, pageable.getSort()));

        TypedQuery<AuditEvent> typed = entityManager.createQuery(query);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());
        List<AuditEvent> content = typed.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AuditEvent> countRoot = countQuery.from(AuditEvent.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicate(cb, countRoot, criteria));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    public List<AuditEvent> findAll(AuditSearchCriteria criteria, int maxRows) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEvent> query = cb.createQuery(AuditEvent.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        query.where(buildPredicate(cb, root, criteria));
        query.orderBy(cb.desc(root.get("occurredAt")));

        TypedQuery<AuditEvent> typed = entityManager.createQuery(query);
        typed.setMaxResults(maxRows);
        return typed.getResultList();
    }

    private static List<jakarta.persistence.criteria.Order> toOrders(
            CriteriaBuilder cb, Root<AuditEvent> root, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of(cb.desc(root.get("occurredAt")));
        }
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (order.isAscending()) {
                orders.add(cb.asc(root.get(order.getProperty())));
            } else {
                orders.add(cb.desc(root.get(order.getProperty())));
            }
        }
        return orders;
    }

    private static Predicate buildPredicate(CriteriaBuilder cb, Root<AuditEvent> root, AuditSearchCriteria c) {
        List<Predicate> parts = new ArrayList<>();
        if (c.occurredFrom() != null) {
            parts.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), c.occurredFrom()));
        }
        if (c.occurredTo() != null) {
            parts.add(cb.lessThanOrEqualTo(root.get("occurredAt"), c.occurredTo()));
        }
        if (c.actorUserId() != null) {
            parts.add(cb.equal(root.get("actorUserId"), c.actorUserId()));
        }
        if (c.actionType() != null) {
            parts.add(cb.equal(root.get("actionType"), c.actionType()));
        }
        if (c.outcome() != null) {
            parts.add(cb.equal(root.get("outcome"), c.outcome()));
        }
        if (c.scopedToActors()) {
            parts.add(cb.isNotNull(root.get("actorUserId")));
            if (c.visibleActorIds() == null || c.visibleActorIds().isEmpty()) {
                parts.add(cb.disjunction());
            } else {
                parts.add(root.get("actorUserId").in(c.visibleActorIds()));
            }
        }
        return cb.and(parts.toArray(Predicate[]::new));
    }
}
