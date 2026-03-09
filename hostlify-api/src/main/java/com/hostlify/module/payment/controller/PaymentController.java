package com.hostlify.module.payment.controller;

import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/dues/hosteller/{hostellerId}")
    public ApiResponse<List<PaymentDue>> getDuesByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(paymentService.getDuesByHosteller(hostellerId));
    }

    @GetMapping("/dues/hostel/{hostelId}/pending")
    public ApiResponse<List<PaymentDue>> getPendingDuesByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(paymentService.getPendingDuesByHostel(hostelId));
    }

    @GetMapping("/dues/{id}")
    public ApiResponse<PaymentDue> getDue(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.getDue(id));
    }

    @PostMapping("/dues")
    public ApiResponse<PaymentDue> createDue(@RequestBody PaymentDue due) {
        return ApiResponse.ok(paymentService.createDue(due));
    }

    @PutMapping("/dues/{id}/mark-paid")
    public ApiResponse<PaymentDue> markPaid(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.markPaid(id));
    }

    @GetMapping("/orders/hosteller/{hostellerId}")
    public ApiResponse<List<PaymentRazorpayOrder>> getOrders(@PathVariable Long hostellerId) {
        return ApiResponse.ok(paymentService.getOrdersByHosteller(hostellerId));
    }

    @PostMapping("/orders")
    public ApiResponse<PaymentRazorpayOrder> createOrder(@RequestBody PaymentRazorpayOrder order) {
        return ApiResponse.ok(paymentService.createOrder(order));
    }

    @PutMapping("/orders/{id}/status")
    public ApiResponse<PaymentRazorpayOrder> updateStatus(@PathVariable Long id,
                                                            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(paymentService.updateOrderStatus(id, body.get("status"), body.get("razorpayPaymentId")));
    }
}
