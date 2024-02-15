package org.sbot.utils;

import java.util.ArrayList;
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
        return StreamSupport.stream(over(splitSize, recycleSubList, inputStream.spliterator()), false);
    }

    public static <T> PartitionSpliterator<T> over(int splitSize, boolean recycleSubList, Spliterator<T> source) {
        return new PartitionSpliterator<T>(source, splitSize, recycleSubList);
    }

    @FunctionalInterface
    private interface RecyclableSupplier<T> extends Supplier<List<T>> {
    }

    private final Spliterator<T> source;
    private final Supplier<List<T>> listFactory;
    private final int splitSize;

    private PartitionSpliterator(Spliterator<T> source, int splitSize, boolean recycleSubList) {
        this.source = requireNonNull(source);
        this.splitSize = Math.max(1, splitSize);
        if(recycleSubList) {
            List<T>[] list = new List[1];
            listFactory = (RecyclableSupplier<T>) () -> {
                list[0] = null != list[0] ? list[0] : new ArrayList<>(splitSize);
                list[0].clear();
                return list[0];
            };
        } else {
            listFactory = () -> new ArrayList<>(splitSize);
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
                .map(split -> new PartitionSpliterator<>(split, splitSize, isRecyclable()))
                .orElse(null);
    }

    @Override
    public long estimateSize() {
        return Math.ceilDiv(source.estimateSize(), splitSize);
    }

    @Override
    public int characteristics() {
        return source.characteristics() & ~Spliterator.SORTED;
    }
}
