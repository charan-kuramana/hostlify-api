package com.hostlify.module.resident.feedback;

import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/feedback")
@Tag(name = "Resident feedback")
public class FeedbackController {

    private final FeedbackService service;
    private final PaymentService paymentService;

    public FeedbackController(FeedbackService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Hosteller: meals, fixed complaints and past stays waiting for a rating")
    public ApiResponse<Map<String, Object>> pending() {
        return ApiResponse.ok(service.pending(paymentService.profileIdOf(HostelAccess.principal().getUserId())));
    }
}
