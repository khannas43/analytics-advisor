package gov.rajasthan.smart.srse.metadata.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "metadata_sync_run")
public class MetadataSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_trigger", nullable = false, length = 32)
    private MetadataSyncTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_outcome", nullable = false, length = 32)
    private MetadataSyncOutcome overallOutcome;

    protected MetadataSyncRun() {
    }

    public MetadataSyncRun(Instant startedAt, MetadataSyncTrigger trigger, MetadataSyncOutcome overallOutcome) {
        this.startedAt = startedAt;
        this.trigger = trigger;
        this.overallOutcome = overallOutcome;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public MetadataSyncTrigger getTrigger() {
        return trigger;
    }

    public MetadataSyncOutcome getOverallOutcome() {
        return overallOutcome;
    }

    public void setOverallOutcome(MetadataSyncOutcome overallOutcome) {
        this.overallOutcome = overallOutcome;
    }
}
