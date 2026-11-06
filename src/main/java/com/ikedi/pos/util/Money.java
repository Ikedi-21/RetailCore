package com.ikedi.pos.util;

import com.ikedi.pos.exception.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Money arithmetic rules for the whole system.
 *
 * <ul>
 *   <li>D1: BigDecimal built from text, never from a double.
 *   <li>D2: scale 2, HALF_UP, rounded per line.
 *   <li>D4: compare with compareTo, never equals.
 * </ul>
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String SYMBOL = "\u20A6"; // naira sign

    private Money() {
    }

    /** Rounds to scale 2 using HALF_UP. */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    /** ratePercent percent of amount (7.5 means 7.5 percent), multiplied first and rounded once. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal ratePercent) {
        return amount.multiply(ratePercent).divide(HUNDRED, SCALE, ROUNDING);
    }

    /**
     * Parses user or file input into an amount with two decimals. More than two meaningful
     * decimals is rejected instead of silently rounded.
     */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("An amount is required");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Not a valid amount: '" + text.trim() + "'");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException("An amount can have at most " + SCALE + " decimal places: " + text.trim());
        }
        return round(value);
    }

    /** Display format, for example the naira sign then 1,075.00. */
    public static String format(BigDecimal amount) {
        return String.format(Locale.US, "%s%,.2f", SYMBOL, round(amount));
    }
}
