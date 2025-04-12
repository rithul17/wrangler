/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */


package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.wrangler.api.annotations.PublicEvolving;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a time duration value parsed from a directive argument (e.g., "150ms", "2.5s", "1min").
 * Provides the value in a canonical unit (nanoseconds).
 */
@PublicEvolving
public class TimeDuration implements Token, Serializable {
    private static final long serialVersionUID = -6873459801234567891L; // Example UID

    // Regex to capture the numeric part and the required unit.
    // Group 1: Numeric value (integer or decimal)
    // Group 2: Unit (ms, s, min, h, d - case insensitive)
    private static final Pattern DURATION_PATTERN = Pattern.compile("^([+-]?\\d*\\.?\\d+)\\s*(ms|s|min|h|d)$", Pattern.CASE_INSENSITIVE);

    private final String originalValue;
    private final long nanoseconds;

    /**
     * Parses the text representation of a time duration.
     *
     * @param text The string to parse (e.g., "150ms", "2.5s", "1min", "1h", "0.5d").
     * @throws IllegalArgumentException if the text is not a valid time duration format.
     */
    public TimeDuration(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text for TimeDuration cannot be null or empty.");
        }
        this.originalValue = text;
        this.nanoseconds = parseNanoseconds(text.trim());
    }

    private long parseNanoseconds(String text) {
        Matcher matcher = DURATION_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid time duration format: '" + text + "'. Expected format like '10ms', '2.5s', '1min', '1h', '0.5d'.");
        }

        String numericPart = matcher.group(1);
        String unitPart = matcher.group(2);

        double value;
        try {
            value = Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            // Should not happen if regex matches, but handle defensively.
            throw new IllegalArgumentException("Invalid numeric value in time duration: '" + numericPart + "'", e);
        }

        String unitLower = unitPart.toLowerCase();
        TimeUnit sourceUnit;

        switch (unitLower) {
            case "d":
                sourceUnit = TimeUnit.DAYS;
                break;
            case "h":
                sourceUnit = TimeUnit.HOURS;
                break;
            case "min":
                sourceUnit = TimeUnit.MINUTES;
                break;
            case "s":
                sourceUnit = TimeUnit.SECONDS;
                break;
            case "ms":
                sourceUnit = TimeUnit.MILLISECONDS;
                break;
            default:
                // Should not happen if regex matches.
                throw new IllegalArgumentException("Unknown time unit: '" + unitPart + "' in '" + text + "'");
        }

        // Convert the double value to the source unit's base unit count first,
        // then convert to nanoseconds to minimize precision issues with large multipliers.
        // E.g., convert 1.5 days to whole milliseconds first, then to nanos.
        // Or, more simply, calculate nanos per unit and multiply by the double value.
        long nanosPerUnit = sourceUnit.toNanos(1);
        double totalNanosDouble = value * nanosPerUnit;

        // Round to the nearest nanosecond.
        return Math.round(totalNanosDouble);
    }

    /**
     * @return The time duration value converted to the canonical unit (nanoseconds).
     */
    public long getNanoseconds() {
        return nanoseconds;
    }

    /**
     * Returns the canonical value (nanoseconds) as a Long.
     *
     * @return Nanosecond value as Long.
     */
    @Override
    public Long value() {
        return nanoseconds;
    }

    /**
     * @return The original string value that was parsed.
     */
    public String getOriginalValue() {
        return originalValue;
    }

    @Override
    public TokenType type() {
        return TokenType.TIME_DURATION; // Assuming TIME_DURATION is added to TokenType enum
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type().name());
        object.addProperty("original_value", originalValue);
        object.addProperty("value_nanos", nanoseconds);
        return object;
    }

    @Override
    public String toString() {
        return "TimeDuration{" +
                "originalValue='" + originalValue + '\'' +
                ", nanoseconds=" + nanoseconds +
                '}';
    }

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeDuration that = (TimeDuration) o;
        return nanoseconds == that.nanoseconds &&
                Objects.equals(originalValue, that.originalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalValue, nanoseconds);
    }
}
