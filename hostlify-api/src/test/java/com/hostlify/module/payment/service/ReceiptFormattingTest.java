package com.hostlify.module.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptFormattingTest {

    @Test
    void indianDigitGrouping() {
        assertEquals("0.00", ReceiptPdfService.money(BigDecimal.ZERO));
        assertEquals("999.00", ReceiptPdfService.money(new BigDecimal("999")));
        assertEquals("6,500.00", ReceiptPdfService.money(new BigDecimal("6500")));
        assertEquals("1,25,000.50", ReceiptPdfService.money(new BigDecimal("125000.5")));
        assertEquals("1,23,45,678.00", ReceiptPdfService.money(new BigDecimal("12345678")));
    }

    @Test
    void amountInWords() {
        assertEquals("Six Thousand Five Hundred", ReceiptPdfService.inWords(new BigDecimal("6500")));
        assertEquals("One Lakh Twenty Five Thousand and Fifty Paise", ReceiptPdfService.inWords(new BigDecimal("125000.50")));
        assertEquals("Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine",
                ReceiptPdfService.inWords(new BigDecimal("123456789")));
        assertEquals("Zero", ReceiptPdfService.inWords(BigDecimal.ZERO));
    }
}
