package gov.rajasthan.smart.srse.otp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/otp-gateway")
public class OtpGatewayAdminController {

    private final OtpGatewayAdminService gatewayAdminService;

    public OtpGatewayAdminController(OtpGatewayAdminService gatewayAdminService) {
        this.gatewayAdminService = gatewayAdminService;
    }

    @GetMapping("/smtp")
    public OtpGatewayAdminService.SmtpGatewayView smtp() {
        return gatewayAdminService.getSmtp();
    }

    @PutMapping("/smtp")
    public OtpGatewayAdminService.SmtpGatewayView saveSmtp(
            @RequestBody OtpGatewayAdminService.SmtpGatewayUpdateRequest request) {
        return gatewayAdminService.saveSmtp(request);
    }

    @PostMapping("/smtp/test")
    public OtpGatewayAdminService.GatewayTestResult testSmtp() {
        return gatewayAdminService.testSmtp();
    }

    @GetMapping("/sms")
    public OtpGatewayAdminService.SmsGatewayView sms() {
        return gatewayAdminService.getSms();
    }

    @PutMapping("/sms")
    public OtpGatewayAdminService.SmsGatewayView saveSms(
            @RequestBody OtpGatewayAdminService.SmsGatewayUpdateRequest request) {
        return gatewayAdminService.saveSms(request);
    }

    @PostMapping("/sms/test")
    public OtpGatewayAdminService.GatewayTestResult testSms() {
        return gatewayAdminService.testSms();
    }
}
