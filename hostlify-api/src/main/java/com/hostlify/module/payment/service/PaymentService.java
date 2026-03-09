package com.hostlify.module.payment.service;

import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRazorpayOrderRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentDueRepository dueRepo;
    private final PaymentRazorpayOrderRepository orderRepo;

    public PaymentService(PaymentDueRepository dueRepo, PaymentRazorpayOrderRepository orderRepo) {
        this.dueRepo = dueRepo;
        this.orderRepo = orderRepo;
    }

    public List<PaymentDue> getDuesByHosteller(Long hostellerId) {
        return dueRepo.findByHostellerIdOrderByDueDateDesc(hostellerId);
    }

    public List<PaymentDue> getPendingDuesByHostel(Long hostelId) {
        return dueRepo.findByHostelIdAndStatus(hostelId, "UNPAID");
    }

    public PaymentDue getDue(Long id) {
        return dueRepo.findById(id).orElseThrow(() -> new AppException("Due not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PaymentDue createDue(PaymentDue due) {
        return dueRepo.save(due);
    }

    @Transactional
    public PaymentDue markPaid(Long id) {
        PaymentDue due = getDue(id);
        due.setStatus("PAID");
        return dueRepo.save(due);
    }

    public List<PaymentRazorpayOrder> getOrdersByHosteller(Long hostellerId) {
        return orderRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    @Transactional
    public PaymentRazorpayOrder createOrder(PaymentRazorpayOrder order) {
        return orderRepo.save(order);
    }

    @Transactional
    public PaymentRazorpayOrder updateOrderStatus(Long id, String status, String razorpayPaymentId) {
        PaymentRazorpayOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        order.setStatus(status);
        if (razorpayPaymentId != null) order.setRazorpayResponseJson(razorpayPaymentId);
        return orderRepo.save(order);
    }
}
