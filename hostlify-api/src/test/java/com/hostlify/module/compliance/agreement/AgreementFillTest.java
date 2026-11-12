package com.hostlify.module.compliance.agreement;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgreementFillTest {

    @Test
    void fillsPlaceholdersAndBlanksMissingOnes() {
        String out = AgreementService.fill("Hi {{tenant_name}}, rent Rs. {{ monthly_rent }} from {{start_date}}.",
                Map.of("tenant_name", "Ravi $1 \\ Kumar", "monthly_rent", "6500"));
        assertEquals("Hi Ravi $1 \\ Kumar, rent Rs. 6500 from ________.", out);
    }

    @Test
    void defaultTemplateUsesOnlyKnownPlaceholders() {
        var m = java.util.regex.Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*}}").matcher(AgreementService.DEFAULT_BODY);
        while (m.find()) assertTrue(AgreementService.PLACEHOLDERS.contains(m.group(1)), m.group(1));
    }

    @Test
    void fingerprintIsStable() {
        assertEquals(AgreementService.sha256("abc"), AgreementService.sha256("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AgreementService.sha256("abc"));
    }
}
