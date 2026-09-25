package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.otp.OtpChallengeService;
import gov.rajasthan.smart.srse.otp.OtpPurpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "srse.auth-mode", havingValue = "local")
public class ContactVerificationService {

    private final OtpChallengeService otpChallengeService;
    private final AppUserRepository userRepository;

    public ContactVerificationService(OtpChallengeService otpChallengeService, AppUserRepository userRepository) {
        this.otpChallengeService = otpChallengeService;
        this.userRepository = userRepository;
    }

    @Transactional
    public String sendVerification(AppUser user, OtpPurpose purpose) {
        if (purpose == OtpPurpose.VERIFY_EMAIL) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new IllegalArgumentException("No email address on file");
            }
        } else if (purpose == OtpPurpose.VERIFY_MOBILE) {
            if (user.getMobile() == null || user.getMobile().isBlank()) {
                throw new IllegalArgumentException("No mobile number on file");
            }
        } else {
            throw new IllegalArgumentException("Unsupported verification purpose");
        }
        return otpChallengeService.beginContactVerification(user, purpose).publicChallengeId();
    }

    @Transactional
    public void confirmVerification(AppUser user, String challengeId, String code, OtpPurpose purpose) {
        AppUser verifiedUser = otpChallengeService.verifyAndConsume(challengeId, code, purpose);
        if (!verifiedUser.getId().equals(user.getId())) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        if (purpose == OtpPurpose.VERIFY_EMAIL) {
            verifiedUser.setEmailVerified(true);
        } else if (purpose == OtpPurpose.VERIFY_MOBILE) {
            verifiedUser.setMobileVerified(true);
        }
        verifiedUser.touchUpdatedAt();
        userRepository.save(verifiedUser);
    }
}
