package gov.rajasthan.smart.srse.otp;

public final class ContactMasking {

    private ContactMasking() {
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "••••";
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "••••";
        }
        return "•••••" + digits.substring(digits.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "•••@•••";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        char first = local.isEmpty() ? '?' : local.charAt(0);
        int dot = domain.indexOf('.');
        String domainHead = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return first + "•••@" + domainHead.charAt(0) + "•••" + tld;
    }
}
