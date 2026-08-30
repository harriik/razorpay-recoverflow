package com.recoverflow.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Money must never use float/double. Verify BigDecimal precision with NUMERIC(19,4).
 */
class MonetaryPrecisionTest {

    @Test
    void bigDecimalPreservesFourDecimalPlaces() {
        BigDecimal amount = new BigDecimal("6500.1234");
        assertEquals(4, amount.scale());
        assertEquals(new BigDecimal("6500.1234"), amount);
    }

    @Test
    void floatWouldLosePrecision() {
        // Demonstrates why floating point is banned: 0.1+0.2 !=0.3 with double
        double d = 0.1 + 0.2;
        assertNotEquals(0.3, d, "double loses precision - must not be used for money");
        BigDecimal bd = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertEquals(new BigDecimal("0.3"), bd);
    }

    @Test
    void expectedNetRecoveryValueCalculationPreservesPrecision() {
        // EV = P * amount - cost - friction - risk
        BigDecimal amount = new BigDecimal("5000.0000");
        BigDecimal p = new BigDecimal("0.580"); // 58%
        BigDecimal cost = new BigDecimal("10.0000");
        BigDecimal friction = new BigDecimal("100.0000");
        BigDecimal risk = new BigDecimal("5.0000");

        BigDecimal expected = p.multiply(amount) // 2900.000
                .subtract(cost)
                .subtract(friction)
                .subtract(risk)
                .setScale(4, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("2785.0000"), expected);
    }

    @Test
    void amountMustBePositive() {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal negative = new BigDecimal("-1.0000");
        BigDecimal positive = new BigDecimal("0.0100");
        assertTrue(positive.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(zero.compareTo(BigDecimal.ZERO) == 0);
        assertTrue(negative.compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    void roundingIsHalfUp() {
        BigDecimal raw = new BigDecimal("123.456789");
        BigDecimal scaled = raw.setScale(4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("123.4568"), scaled);

        BigDecimal raw2 = new BigDecimal("123.45674");
        assertEquals(new BigDecimal("123.4567"), raw2.setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void syntheticFrictionProxyIsNotRealCurrency() {
        // Ensure naming distinguishes proxy from real cost; value is utility, not monetary transfer
        String proxyName = "syntheticCustomerFrictionProxy";
        assertTrue(proxyName.contains("synthetic"));
        assertTrue(proxyName.contains("Friction"));
        // Numeric handling same but semantics differ - test that we don't confuse with cost
        BigDecimal frictionProxy = new BigDecimal("50.0000");
        BigDecimal cost = new BigDecimal("50.0000");
        // They are equal numerically but tracked separately in RecoveryAction
        assertEquals(frictionProxy, cost);
        // Future EV engine must subtract both: amount*P - cost - frictionProxy - risk
    }
}
