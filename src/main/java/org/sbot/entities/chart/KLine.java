package org.sbot.entities.chart;

import org.jetbrains.annotations.NotNull;
import org.sbot.utils.MutableDecimal;

import static org.sbot.utils.ArgumentValidator.requireEpoch;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public abstract class KLine {

    private static final class VolatileKLineImpl extends KLine {
        public volatile long openTime;
        public volatile long closeTime;
        // decimals are stored as their compact value part (mantissa) with a scale
        private volatile long openValue;
        private volatile long closeValue;
        private volatile long highValue;
        private volatile long lowValue;
        private volatile byte openScale;
        private volatile byte closeScale;
        private volatile byte highScale;
        private volatile byte lowScale;

        @Override
        public long openTime() {
            return openTime;
        }

        @Override
        public void openTime(long openTime) {
            this.openTime = openTime;
        }

        @Override
        public long closeTime() {
            return closeTime;
        }

        @Override
        public void closeTime(long closeTime) {
            this.closeTime = closeTime;
        }

        @Override
        public long openValue() {
            return openValue;
        }

        @Override
        public void openValue(long openValue) {
            this.openValue = openValue;
        }

        @Override
        public long closeValue() {
            return closeValue;
        }

        @Override
        public void closeValue(long closeValue) {
            this.closeValue = closeValue;
        }

        @Override
        public long highValue() {
            return highValue;
        }

        @Override
        public void highValue(long highValue) {
            this.highValue = highValue;
        }

        @Override
        public long lowValue() {
            return lowValue;
        }

        @Override
        public void lowValue(long lowValue) {
            this.lowValue = lowValue;
        }

        @Override
        public byte openScale() {
            return openScale;
        }

        @Override
        public void openScale(byte openScale) {
            this.openScale = openScale;
        }

        @Override
        public byte closeScale() {
            return closeScale;
        }

        @Override
        public void closeScale(byte closeScale) {
            this.closeScale = closeScale;
        }

        @Override
        public byte highScale() {
            return highScale;
        }

        @Override
        public void highScale(byte highScale) {
            this.highScale = highScale;
        }

        @Override
        public byte lowScale() {
            return lowScale;
        }

        @Override
        public void lowScale(byte lowScale) {
            this.lowScale = lowScale;
        }
    }

    private static final class KLineImpl extends KLine {
        public long openTime;
        public long closeTime;
        // decimals are stored as their compact value part (mantissa) with a scale
        private long openValue;
        private long closeValue;
        private long highValue;
        private long lowValue;
        private byte openScale;
        private byte closeScale;
        private byte highScale;
        private byte lowScale;

        @Override
        public long openTime() {
            return openTime;
        }

        @Override
        public void openTime(long openTime) {
            this.openTime = openTime;
        }

        @Override
        public long closeTime() {
            return closeTime;
        }

        @Override
        public void closeTime(long closeTime) {
            this.closeTime = closeTime;
        }

        @Override
        public long openValue() {
            return openValue;
        }

        @Override
        public void openValue(long openValue) {
            this.openValue = openValue;
        }

        @Override
        public long closeValue() {
            return closeValue;
        }

        @Override
        public void closeValue(long closeValue) {
            this.closeValue = closeValue;
        }

        @Override
        public long highValue() {
            return highValue;
        }

        @Override
        public void highValue(long highValue) {
            this.highValue = highValue;
        }

        @Override
        public long lowValue() {
            return lowValue;
        }

        @Override
        public void lowValue(long lowValue) {
            this.lowValue = lowValue;
        }

        @Override
        public byte openScale() {
            return openScale;
        }

        @Override
        public void openScale(byte openScale) {
            this.openScale = openScale;
        }

        @Override
        public byte closeScale() {
            return closeScale;
        }

        @Override
        public void closeScale(byte closeScale) {
            this.closeScale = closeScale;
        }

        @Override
        public byte highScale() {
            return highScale;
        }

        @Override
        public void highScale(byte highScale) {
            this.highScale = highScale;
        }

        @Override
        public byte lowScale() {
            return lowScale;
        }

        @Override
        public void lowScale(byte lowScale) {
            this.lowScale = lowScale;
        }
    }

    public static KLine emptyVolatile() {
        return new VolatileKLineImpl();
    }

    public static KLine empty() {
        return new KLineImpl();
    }

    public abstract long openTime();

    public abstract void openTime(long openTime);

    public abstract long closeTime();

    public abstract void closeTime(long closeTime);

    public abstract long openValue();

    public abstract void openValue(long openValue);

    public abstract long closeValue();

    public abstract void closeValue(long closeValue) ;

    public abstract long highValue();

    public abstract void highValue(long highValue);

    public abstract long lowValue();

    public abstract void lowValue(long lowValue);

    public abstract byte openScale();

    public abstract void openScale(byte openScale);

    public abstract byte closeScale();

    public abstract void closeScale(byte closeScale);

    public abstract  byte highScale();

    public abstract void highScale(byte highScale);

    public abstract byte lowScale();

    public abstract void lowScale(byte lowScale);


    public final void validate(@NotNull MutableDecimal priceBuffer) {
        requireEpoch(openTime());
        requireEpoch(closeTime());
        if(openTime() > closeTime()) {
            throw new IllegalArgumentException("openTime is after closeTime");
        }
        requirePositive(openValue());
        requirePositive(closeValue());
        requirePositive(highValue());
        requirePositive(lowValue());
        if(priceBuffer.set(lowValue(), lowScale()).compareTo(highValue(), highScale()) > 0) {
            throw new IllegalArgumentException("low is higher than high");
        }
    }

    public final void reset() {
        openTime(0L);
        closeTime(0L);
        openValue(0L);
        closeValue(0L);
        highValue(0L);
        lowValue(0L);
        openScale((byte) 0);
        closeScale((byte) 0);
        highScale((byte) 0);
        lowScale((byte) 0);
    }

    @NotNull
    public final KLine withValues(@NotNull KLine kLine) {
        openTime(kLine.openTime());
        closeTime(kLine.closeTime());
        openValue(kLine.openValue());
        closeValue(kLine.closeValue());
        highValue(kLine.highValue());
        lowValue(kLine.lowValue());
        openScale(kLine.openScale());
        closeScale(kLine.closeScale());
        highScale(kLine.highScale());
        lowScale(kLine.lowScale());
        return this;
    }

    public final DatedPrice datedClose() {
        return new DatedPrice(MutableDecimal.of(closeValue(), closeScale()), closeTime());
    }

    @NotNull
    public final MutableDecimal getOpen(@NotNull MutableDecimal decimal) {
        return decimal.set(openValue(), openScale());
    }

    @NotNull
    public final MutableDecimal getClose(@NotNull MutableDecimal decimal) {
        return decimal.set(closeValue(), closeScale());
    }

    @NotNull
    public final MutableDecimal getHigh(@NotNull MutableDecimal decimal) {
        return decimal.set(highValue(), highScale());
    }

    @NotNull
    public final MutableDecimal getLow(@NotNull MutableDecimal decimal) {
        return decimal.set(lowValue(), lowScale());
    }


    @Override
    public final String toString() {
        return "KLine{" +
                "openTime=" + openTime() +
                ", closeTime=" + closeTime() +
                ", openValue=" + openValue() +
                ", closeValue=" + closeValue() +
                ", highValue=" + highValue() +
                ", lowValue=" + lowValue() +
                ", openScale=" + openScale() +
                ", closeScale=" + closeScale() +
                ", highScale=" + highScale() +
                ", lowScale=" + lowScale() + '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KLine kLine)) return false;
        return openTime() == kLine.openTime() && closeTime() == kLine.closeTime() && openValue() == kLine.openValue() && closeValue() == kLine.closeValue() && highValue() == kLine.highValue() && lowValue() == kLine.lowValue() && openScale() == kLine.openScale() && closeScale() == kLine.closeScale() && highScale() == kLine.highScale() && lowScale() == kLine.lowScale();
    }

    @Override
    public final int hashCode() {
        // probably a bad idea to use a mutable object as a key in a map
        throw new UnsupportedOperationException();
    }
}
