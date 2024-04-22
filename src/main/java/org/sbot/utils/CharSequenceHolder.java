package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public record CharSequenceHolder(@NotNull CharSequence charSequence) {

    public CharSequenceHolder {
        requireNonNull(charSequence);
    }

    @Override
    public int hashCode() {
        return switch (charSequence.length()) {
            case 0 -> 0;
            case 1 -> charSequence.charAt(0) & 0xff;
            default -> {
                int hash = charSequence.charAt(0);
                for (int i = charSequence.length(); --i != 0; ) {
                    hash = 31 * hash + charSequence.charAt(i);
                }
                yield hash;
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CharSequenceHolder csh &&
                equals(charSequence, csh.charSequence, 0, csh.charSequence.length());
    }

    public static boolean equals(@NotNull CharSequence charSequence, @NotNull CharSequence value, int beginIndex, int endIndex) {
        if (charSequence.length() != endIndex - beginIndex) {
            return false;
        }
        for (int i = charSequence.length(); i-- != 0; ) {
            if (charSequence.charAt(i) != value.charAt(beginIndex + i)) {
                return false;
            }
        }
        return true;
    }
}
