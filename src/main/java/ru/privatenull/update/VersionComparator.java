package ru.privatenull.update;

import java.util.Locale;

final class VersionComparator {
    private VersionComparator() {
    }

    static int compare(String leftValue, String rightValue) {
        Version left = Version.parse(leftValue);
        Version right = Version.parse(rightValue);
        for (int index = 0; index < Math.max(left.parts.length, right.parts.length); index++) {
            int leftPart = index < left.parts.length ? left.parts[index] : 0;
            int rightPart = index < right.parts.length ? right.parts[index] : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        if (left.snapshot != right.snapshot) return left.snapshot ? -1 : 1;
        return 0;
    }

    private record Version(int[] parts, boolean snapshot) {
        private static Version parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            boolean snapshot = normalized.contains("snapshot");
            String[] raw = normalized.replaceFirst("^v", "").split("[-+]", 2)[0].split("\\.");
            int[] parts = new int[raw.length];
            for (int i = 0; i < raw.length; i++) {
                String digits = raw[i].replaceAll("\\D", "");
                try {
                    parts[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                    parts[i] = 0;
                }
            }
            return new Version(parts, snapshot);
        }
    }
}
