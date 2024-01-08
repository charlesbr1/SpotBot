package org.sbot.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;


/*
    Stream wrapper that allows to split a stream (or a collection) in sub lists of given size.
    For instance PartitionSpliterator.split(10, stream).forEach(subList -> ...) will perform consecutive forEach calls
    with sub lists of maximum 10 elements.
 */
public final class PartitionSpliterator<T> implements Spliterator<List<T>> {

    public static <T> Stream<List<T>> split(int splitSize, Stream<T> inputStream) {
        return split(splitSize, false, inputStream);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, Stream<T> inputStream) {
        return split(splitSize, recycleSubList, true, inputStream);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, boolean reserveSpace, Stream<T> inputStream) {
        return StreamSupport.stream(over(splitSize, recycleSubList, reserveSpace, inputStream.spliterator()), false);
    }

    public static <T> Stream<List<T>> split(int splitSize, Iterable<T> inputIterable) {
        return split(splitSize, false, inputIterable);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, Iterable<T> inputIterable) {
        return split(splitSize, recycleSubList, true, inputIterable);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, boolean reserveSpace, Iterable<T> inputIterable) {
        return StreamSupport.stream(over(splitSize, recycleSubList, reserveSpace, inputIterable.spliterator()), false);
    }

    public static <T> Stream<List<T>> split(int splitSize, Collection<T> inputCollection) {
        return split(splitSize, false, inputCollection);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, Collection<T> inputCollection) {
        return split(splitSize, recycleSubList, true, inputCollection);
    }

    public static <T> Stream<List<T>> split(int splitSize, boolean recycleSubList, boolean reserveSpace, Collection<T> inputCollection) {
        return StreamSupport.stream(over(splitSize, recycleSubList, reserveSpace, inputCollection.spliterator()), false);
    }

    public static <T> PartitionSpliterator<T> over(int splitSize, Spliterator<T> source) {
        return over(splitSize, false, source);
    }

    public static <T> PartitionSpliterator<T> over(int splitSize, boolean recycleSubList, Spliterator<T> source) {
        return over(splitSize, recycleSubList, true, source);
    }

    public static <T> PartitionSpliterator<T> over(int splitSize, boolean recycleSubList, boolean reserveSpace, Spliterator<T> source) {
        return new PartitionSpliterator<T>(source, splitSize, recycleSubList, reserveSpace);
    }

    @FunctionalInterface
    private interface RecyclableSupplier<T> extends Supplier<List<T>> {
    }

    private final Spliterator<T> source;
    private final Supplier<List<T>> listFactory;
    private final int splitSize;
    private final boolean reserveSpace;

    private PartitionSpliterator(Spliterator<T> source, int splitSize, boolean recycleSubList, boolean reserveSpace) {
        this.source = requireNonNull(source);
        this.splitSize = Math.max(1, splitSize);
        this.reserveSpace = reserveSpace;
        if(recycleSubList) {
            List<T>[] list = new List[1];
            listFactory = (RecyclableSupplier<T>) () -> {
                list[0] = null != list[0] ? list[0] : new ArrayList<>(reserveSpace ? splitSize : 10);
                list[0].clear();
                return list[0];
            };
        } else {
            listFactory = () -> new ArrayList<>(reserveSpace ? splitSize : 10);
        }
    }

    public boolean isRecyclable() {
        return listFactory instanceof RecyclableSupplier;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        List<T>[] list = new List[1];
        for (int i = splitSize; i-- != 0 && source.tryAdvance(v -> {
            list[0] = null != list[0] ? list[0] : listFactory.get();
            list[0].add(v);
        }););
        if(null != list[0]) {
            action.accept(list[0]);
        }
        return null != list[0];
    }

    @Override
    public Spliterator<List<T>> trySplit() {
        return ofNullable(source.trySplit())
                .map(split -> new PartitionSpliterator<>(split, splitSize, reserveSpace, isRecyclable()))
                .orElse(null);
    }

    @Override
    public long estimateSize() {
        return ceilDiv(source.estimateSize(), splitSize);
    }

    @Override
    public int characteristics() {
        return source.characteristics() & ~Spliterator.SORTED;
    }

    private static long ceilDiv(long dividend, long divisor) {
        return -Math.floorDiv(-dividend, divisor);
    }
}
