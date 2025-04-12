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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a byte size value parsed from a directive argument (e.g., "10KB", "1.5MB").
 * Provides the value in a canonical unit (bytes).
 */
@PublicEvolving
public class ByteSize implements Token, Serializable {
    private static final long serialVersionUID = -5873459801234567890L; // Example UID

    // Regex to capture the numeric part and the optional unit.
    // Group 1: Numeric value (integer or decimal)
    // Group 2: Unit (KB, MB, GB, TB, B, or case variations), optional. Defaults to Bytes if missing.
    private static final Pattern BYTE_PATTERN = Pattern.compile("^([+-]?\\d*\\.?\\d+)\\s*([kKmMgGtT]?[Bb]?)?$");

    private static final long KILO = 1024L;
    private static final long MEGA = KILO * 1024L;
    private static final long GIGA = MEGA * 1024L;
    private static final long TERA = GIGA * 1024L;

    private final String originalValue;
    private final long bytes;

    /**
     * Parses the text representation of a byte size.
     *
     * @param text The string to parse (e.g., "1024", "10KB", "1.5mb", "1gB").
     * @throws IllegalArgumentException if the text is not a valid byte size format.
     */
    public ByteSize(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text for ByteSize cannot be null or empty.");
        }
        this.originalValue = text;
        this.bytes = parseBytes(text.trim());
    }

    private long parseBytes(String text) {
        Matcher matcher = BYTE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid byte size format: '" + text + "'. Expected format like '10KB', '1.5MB', '1024'.");
        }

        String numericPart = matcher.group(1);
        String unitPart = matcher.group(2);

        double value;
        try {
            value = Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            // Should not happen if regex matches, but handle defensively.
            throw new IllegalArgumentException("Invalid numeric value in byte size: '" + numericPart + "'", e);
        }

        long multiplier = 1L; // Default to Bytes

        if (unitPart != null && !unitPart.isEmpty()) {
            String unitLower = unitPart.toLowerCase();
            switch (unitLower) {
                case "b":
                    // multiplier is already 1
                    break;
                case "kb":
                case "k": // Allow shorthand K/M/G/T if followed by nothing or B/b
                    multiplier = KILO;
                    break;
                case "mb":
                case "m":
                    multiplier = MEGA;
                    break;
                case "gb":
                case "g":
                    multiplier = GIGA;
                    break;
                case "tb":
                case "t":
                    multiplier = TERA;
                    break;
                default:
                    // This case might occur if the unit is present but not just 'B/b'
                    // Example: "10 K" - we need to decide if 'K' alone is valid.
                    // Let's treat standalone K/M/G/T as KB/MB/GB/TB for robustness.
                    // If it was something else entirely, the regex wouldn't have matched group 2 correctly
                    // based on the pattern `[kKmMgGtT]?[Bb]?`. Re-checking logic.
                    // The pattern allows "10k" or "10kb".
                    // If it matched, it must be one of the known units. Let's make error explicit if somehow missed.
                     throw new IllegalArgumentException("Unknown byte unit: '" + unitPart + "' in '" + text + "'");
            }
        }
        // Handle potential overflow and precision loss during conversion.
        // Round to the nearest byte.
        return Math.round(value * multiplier);
    }

    /**
     * @return The byte size value converted to the canonical unit (bytes).
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * Returns the canonical value (bytes) as a Long.
     *
     * @return Byte value as Long.
     */
    @Override
    public Long value() {
        return bytes;
    }

    /**
     * @return The original string value that was parsed.
     */
    public String getOriginalValue() {
        return originalValue;
    }

    @Override
    public TokenType type() {
        return TokenType.BYTE_SIZE; // Assuming BYTE_SIZE is added to TokenType enum
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type().name());
        object.addProperty("original_value", originalValue);
        object.addProperty("value_bytes", bytes);
        return object;
    }

    @Override
    public String toString() {
        return "ByteSize{" +
                "originalValue='" + originalValue + '\'' +
                ", bytes=" + bytes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteSize byteSize = (ByteSize) o;
        return bytes == byteSize.bytes &&
                Objects.equals(originalValue, byteSize.originalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalValue, bytes);
    }
}
