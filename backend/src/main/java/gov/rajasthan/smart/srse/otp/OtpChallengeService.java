package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.identity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class OtpChallengeService {

    private final UserOtpChallengeRepository challengeRepository;
    private final OtpCodeGenerator codeGenerator;
    private final OtpDeliveryService deliveryService;
    private final OtpProperties properties;

    public OtpChallengeService(
            UserOtpChallengeRepository challengeRepository,
            OtpCodeGenerator codeGenerator,
            OtpDeliveryService deliveryService,
            OtpProperties properties) {
        this.challengeRepository = challengeRepository;
        this.codeGenerator = codeGenerator;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    @Transactional
    public IssuedChallenge beginLoginChallenge(AppUser user) {
        return issue(user, OtpPurpose.LOGIN, true);
    }

    @Transactional
    public IssuedChallenge beginContactVerification(AppUser user, OtpPurpose purpose) {
        if (purpose != OtpPurpose.VERIFY_EMAIL && purpose != OtpPurpose.VERIFY_MOBILE) {
            throw new IllegalArgumentException("Not a contact verification purpose");
        }
        OtpChannel channel = purpose == OtpPurpose.VERIFY_EMAIL ? OtpChannel.EMAIL : OtpChannel.SMS;
        return issueContact(user, purpose, channel);
    }

    @Transactional
    public IssuedChallenge resend(String publicChallengeId) {
        UserOtpChallenge existing = challengeRepository.findByPublicChallengeId(publicChallengeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or expired challenge"));
        Instant now = Instant.now();
        if (existing.isConsumed() || existing.isExpired(now)) {
            throw new IllegalArgumentException("Unknown or expired challenge");
        }
        if (existing.getLastSentAt() != null) {
            Instant allowed = existing.getLastSentAt().plus(
                    properties.resendCooldownSeconds(), ChronoUnit.SECONDS);
            if (now.isBefore(allowed)) {
                throw new IllegalArgumentException("Please wait before requesting another code");
            }
        }
        AppUser user = existing.getUser();
        String code = codeGenerator.generateSixDigitCode();
        existing.setCodeHash(OtpHasher.hash(user.getId(), existing.getPurpose(), code));
        existing.setExpiresAt(now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES));
        existing.setLastSentAt(now);
        OtpDeliveryService.DeliveryOutcome delivery = deliver(existing.getPurpose(), user, code);
        if (existing.getPurpose() == OtpPurpose.LOGIN && !delivery.anyChannelSucceeded()) {
            throw new OtpDeliveryException(
                    "Verification code could not be sent. Try again or contact an administrator.");
        }
        existing.setDeliveryStatus(delivery.statusSummary());
        challengeRepository.save(existing);
        return new IssuedChallenge(existing.getPublicChallengeId(), existing.getPurpose(), user);
    }

    @Transactional
    public AppUser verifyAndConsume(String publicChallengeId, String code, OtpPurpose expectedPurpose) {
        Instant now = Instant.now();
        UserOtpChallenge challenge = challengeRepository.findByPublicChallengeId(publicChallengeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification code"));
        if (challenge.isConsumed() || challenge.isExpired(now)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        if (challenge.getPurpose() != expectedPurpose) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        if (challenge.getAttemptCount() >= properties.maxVerifyAttempts()) {
            challenge.setConsumedAt(now);
            challengeRepository.save(challenge);
            throw new IllegalArgumentException("Too many attempts — sign in again");
        }
        AppUser user = challenge.getUser();
        if (!OtpHasher.matches(user.getId(), expectedPurpose, code, challenge.getCodeHash())) {
            challenge.incrementAttemptCount();
            if (challenge.getAttemptCount() >= properties.maxVerifyAttempts()) {
                challenge.setConsumedAt(now);
            }
            challengeRepository.save(challenge);
            throw new IllegalArgumentException("Invalid verification code");
        }
        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        return user;
    }

    private IssuedChallenge issue(AppUser user, OtpPurpose purpose, boolean login) {
        Instant now = Instant.now();
        challengeRepository.invalidateActiveForUser(user.getId(), purpose, now);
        String code = codeGenerator.generateSixDigitCode();
        String hash = OtpHasher.hash(user.getId(), purpose, code);
        OtpDeliveryService.DeliveryOutcome delivery = deliver(purpose, user, code);
        if (login && !delivery.anyChannelSucceeded()) {
            throw new OtpDeliveryException(
                    "Verification code could not be sent. Try again or contact an administrator.");
        }
        String publicId = UUID.randomUUID().toString();
        UserOtpChallenge row = new UserOtpChallenge(
                publicId,
                user,
                hash,
                purpose,
                now,
                now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES),
                delivery.statusSummary(),
                now);
        challengeRepository.save(row);
        return new IssuedChallenge(publicId, purpose, user);
    }

    private IssuedChallenge issueContact(AppUser user, OtpPurpose purpose, OtpChannel channel) {
        Instant now = Instant.now();
        challengeRepository.invalidateActiveForUser(user.getId(), purpose, now);
        String code = codeGenerator.generateSixDigitCode();
        String hash = OtpHasher.hash(user.getId(), purpose, code);
        OtpDeliveryService.DeliveryOutcome delivery =
                deliveryService.deliverVerificationCode(user, channel, code);
        if (!delivery.anyChannelSucceeded()) {
            throw new OtpDeliveryException("Verification code could not be sent to that contact.");
        }
        String publicId = UUID.randomUUID().toString();
        UserOtpChallenge row = new UserOtpChallenge(
                publicId,
                user,
                hash,
                purpose,
                now,
                now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES),
                delivery.statusSummary(),
                now);
        challengeRepository.save(row);
        return new IssuedChallenge(publicId, purpose, user);
    }

    private OtpDeliveryService.DeliveryOutcome deliver(OtpPurpose purpose, AppUser user, String code) {
        if (purpose == OtpPurpose.LOGIN) {
            return deliveryService.deliverLoginCode(user, code);
        }
        OtpChannel channel = purpose == OtpPurpose.VERIFY_EMAIL ? OtpChannel.EMAIL : OtpChannel.SMS;
        return deliveryService.deliverVerificationCode(user, channel, code);
    }

    public record IssuedChallenge(String publicChallengeId, OtpPurpose purpose, AppUser user) {
    }
}
