package com.hostlify.module.payment.event;

import com.hostlify.module.payment.entity.PaymentRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentReceivedEvent extends ApplicationEvent {

    private final PaymentRecord paymentRecord;
    private final String hostellerEmail;
    private final String hostellerName;
    private final int periodMonth;
    private final int periodYear;

    public PaymentReceivedEvent(Object source, PaymentRecord paymentRecord,
                                String hostellerEmail, String hostellerName,
                                int periodMonth, int periodYear) {
        super(source);
        this.paymentRecord = paymentRecord;
        this.hostellerEmail = hostellerEmail;
        this.hostellerName = hostellerName;
        this.periodMonth = periodMonth;
        this.periodYear = periodYear;
    }
}
