package com.hostlify.module.payment.event;

import com.hostlify.module.payment.entity.PaymentRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * A resident's direct-UPI payment changed state:
 *  SUBMITTED - resident says they paid; staff should verify it against the owner's UPI app.
 *  REJECTED  - staff could not find the money; the resident is told to check.
 * (CONFIRMED raises a normal PaymentReceivedEvent.)
 */
@Getter
public class UpiPaymentClaimEvent extends ApplicationEvent {

    public enum Kind { SUBMITTED, REJECTED }

    private final Kind kind;
    private final PaymentRecord record;
    private final String hostellerName;
    private final String reason;

    public UpiPaymentClaimEvent(Object source, Kind kind, PaymentRecord record, String hostellerName, String reason) {
        super(source);
        this.kind = kind;
        this.record = record;
        this.hostellerName = hostellerName;
        this.reason = reason;
    }
}
