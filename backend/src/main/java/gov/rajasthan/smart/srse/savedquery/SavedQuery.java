package gov.rajasthan.smart.srse.savedquery;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saved_query")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 1024)
    private String description;

    /**
     * Long text, not a LOB locator.
     *
     * <p>{@code @Lob} on a String makes Hibernate expect {@code oid} on PostgreSQL —
     * a large-object handle stored outside the row — while Liquibase's {@code clob}
     * renders as plain {@code text}. The two disagree, and {@code ddl-auto: validate}
     * refuses to start the application: "found [text], but expecting [oid]". Every
     * test still passed, because the slices mock their repositories and the one
     * integration test that boots against a real PostgreSQL is opt-in.
     *
     * <p>{@code LONGVARCHAR} is what was meant on both engines: {@code text} on
     * PostgreSQL, {@code clob} on DB2, no locator indirection, and it matches what
     * the changeset actually creates.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload_text", nullable = false)
    private String payloadText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public void setPayloadText(String payloadText) {
        this.payloadText = payloadText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
