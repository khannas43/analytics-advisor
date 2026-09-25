package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_otp_challenge")
public class UserOtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_challenge_id", nullable = false, unique = true, length = 36)
    private String publicChallengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "delivery_status", length = 256)
    private String deliveryStatus;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    protected UserOtpChallenge() {
    }

    public UserOtpChallenge(
            String publicChallengeId,
            AppUser user,
            String codeHash,
            OtpPurpose purpose,
            Instant createdAt,
            Instant expiresAt,
            String deliveryStatus,
            Instant lastSentAt) {
        this.publicChallengeId = publicChallengeId;
        this.user = user;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.deliveryStatus = deliveryStatus;
        this.lastSentAt = lastSentAt;
    }

    public Long getId() {
        return id;
    }

    public String getPublicChallengeId() {
        return publicChallengeId;
    }

    public AppUser getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
