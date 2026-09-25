package gov.rajasthan.smart.srse.audit;

import java.util.List;

import org.springframework.data.repository.Repository;

/**
 * Append-only persistence for {@link AuditEvent} (§7.3, decision A9).
 *
 * <p><b>This deliberately does not extend {@code JpaRepository}.</b> That
 * interface inherits {@code delete}, {@code deleteAll}, {@code deleteById} and
 * {@code saveAndFlush}, all of them callable — so a repository that merely
 * declares no mutators of its own still offers every caller a way to erase the
 * audit trail. Declaring no delete method is not the same as having none.
 *
 * <p>Extending Spring Data's bare {@link Repository} marker and naming only the
 * operations the product needs is what makes append-only structural: there is
 * no delete to call, so a future convenience method has to be added
 * deliberately and visibly rather than inherited by accident.
 *
 * <p>Read methods are for the log viewer (7.3.4) and may grow. A mutator may
 * not.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findAll();

    long count();
}
