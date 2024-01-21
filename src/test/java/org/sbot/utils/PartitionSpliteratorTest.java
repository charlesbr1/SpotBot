package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Spliterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class PartitionSpliteratorTest {

    @Test
    void split() {
        assertEquals(0, PartitionSpliterator.split(1, Stream.empty()).count());
        assertEquals(1, PartitionSpliterator.split(1, IntStream.range(0, 1).boxed()).count());
        assertEquals(10, PartitionSpliterator.split(1, IntStream.range(0, 10).boxed()).count());
        assertEquals(1000, PartitionSpliterator.split(1, IntStream.range(0, 1000).boxed()).count());
        assertEquals(1000 / 2, PartitionSpliterator.split(2, IntStream.range(0, 1000).boxed()).count());
        assertEquals(1000 / 4, PartitionSpliterator.split(4, IntStream.range(0, 1000).boxed()).count());
        assertEquals((1000 / 15) + 1, PartitionSpliterator.split(15, IntStream.range(0, 1000).boxed()).count());
        assertEquals(1000 / 250, PartitionSpliterator.split(250, IntStream.range(0, 1000).boxed()).count());
        assertEquals(1, PartitionSpliterator.split(1234, IntStream.range(0, 1000).boxed()).count());

        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(1, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(2, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(10, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(31, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(1000, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(1345, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());

        assertEquals(1, PartitionSpliterator.split(1, IntStream.range(0, 1).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(1, PartitionSpliterator.split(1, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(2, PartitionSpliterator.split(2, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(4, PartitionSpliterator.split(4, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(15, PartitionSpliterator.split(15, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(250, PartitionSpliterator.split(250, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(1000, PartitionSpliterator.split(1234, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());

        assertEquals(10, PartitionSpliterator.split(1, true, IntStream.range(0, 10).boxed()).count());
        assertEquals((1000 / 15) + 1, PartitionSpliterator.split(15, true, IntStream.range(0, 1000).boxed()).count());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(10, true, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(IntStream.range(0, 1000).boxed().toList(), PartitionSpliterator.split(31, true, IntStream.range(0, 1000).boxed()).flatMap(List::stream).toList());
        assertEquals(4, PartitionSpliterator.split(4, true, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());
        assertEquals(15, PartitionSpliterator.split(15, true, IntStream.range(0, 1000).boxed()).findFirst().orElse(emptyList()).size());

        assertEquals(1001 % 15, PartitionSpliterator.split(15, IntStream.range(0, 1001).boxed()).reduce((a, b) -> {
            assertNotSame(a, b);
            return b;
        }).orElse(emptyList()).size());

        assertEquals(1001 % 15, PartitionSpliterator.split(15, true, IntStream.range(0, 1001).boxed()).reduce((a, b) -> {
            assertSame(a, b);
            return b;
        }).orElse(emptyList()).size());
    }

    @Test
    void isRecyclable() {
        assertFalse(PartitionSpliterator.over(1, false, emptyList().spliterator()).isRecyclable());
        assertTrue(PartitionSpliterator.over(1, true, emptyList().spliterator()).isRecyclable());
    }

    @Test
    void trySplit() {
        assertEquals(List.of(1, 2),
                StreamSupport.stream(PartitionSpliterator.over(10, false,
                                List.of(1, 2, 3, 4).spliterator()).trySplit(), false)
                        .flatMap(List::stream).toList());
    }

    @Test
    void estimateSize() {
        assertEquals(0, PartitionSpliterator.over(10, false,
                emptyList().spliterator()).estimateSize());
        assertEquals(4, PartitionSpliterator.over(1, false,
                List.of(1, 2, 3, 4).spliterator()).estimateSize());
        assertEquals(2, PartitionSpliterator.over(2, false,
                List.of(1, 2, 3, 4).spliterator()).estimateSize());
        assertEquals(2, PartitionSpliterator.over(3, false,
                List.of(1, 2, 3, 4).spliterator()).estimateSize());
        assertEquals(1, PartitionSpliterator.over(4, false,
                List.of(1, 2, 3, 4).spliterator()).estimateSize());
    }

    @Test
    void characteristics() {
        assertEquals(emptyList().spliterator().characteristics() & ~Spliterator.SORTED,
                PartitionSpliterator.over(1, false,
                        emptyList().spliterator()).characteristics());
    }
}