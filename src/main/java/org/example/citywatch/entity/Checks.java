package org.example.citywatch.entity;


/** Small validation helpers shared by domain values. */
public final class Checks {
    private Checks() { }
    public static String text(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Required text must not be blank");
        }
        return value;
    }
}
