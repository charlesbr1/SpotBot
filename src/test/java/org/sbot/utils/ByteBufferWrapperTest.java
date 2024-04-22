package org.sbot.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ByteBufferWrapperTest {

    @Test
    void buffer() {
        var wrapper = new ByteBufferWrapper();
        assertThrows(NullPointerException.class, () -> wrapper.setBuffer(null));
        var byteBuffer = mock(ByteBuf.class);
        assertNull(wrapper.buffer());
        wrapper.setBuffer(byteBuffer);
        assertEquals(byteBuffer, wrapper.buffer());
    }

    @Test
    void length() {
        var wrapper = new ByteBufferWrapper();
        assertThrows(NullPointerException.class, wrapper::length);
        var byteBuffer = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        assertEquals(4, wrapper.length());
        byteBuffer = Unpooled.copiedBuffer("test33", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        assertEquals(6, wrapper.length());
    }

    @Test
    void charAt() {
        var wrapper = new ByteBufferWrapper();
        assertThrows(NullPointerException.class, () -> wrapper.charAt(0));
        var byteBuffer = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        assertEquals('t', wrapper.charAt(0));
        assertEquals('e', wrapper.charAt(1));
        assertEquals('s', wrapper.charAt(2));
        assertEquals('t', wrapper.charAt(3));
    }

    @Test
    void subSequence() {
        var wrapper = new ByteBufferWrapper();
        assertThrows(NullPointerException.class, () -> wrapper.subSequence(0, 1));
        var byteBuffer = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        var slice = wrapper.subSequence(1, 3);
        assertEquals(2, slice.length());
        assertEquals('e', slice.charAt(0));
        assertEquals('s', slice.charAt(1));
        assertEquals("es", slice.toString());

        slice = wrapper.subSequence(3, 3);
        assertEquals("", slice.toString());
        slice = wrapper.subSequence(2, 3);
        assertEquals("s", slice.toString());
        assertThrows(IllegalArgumentException.class, () -> wrapper.subSequence(3, 2));
    }

    @Test
    void testToString() {
        var wrapper = new ByteBufferWrapper();
        assertThrows(NullPointerException.class, wrapper::toString);
        var byteBuffer = Unpooled.copiedBuffer("test", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        assertEquals("test", wrapper.toString());
        byteBuffer = Unpooled.copiedBuffer("next test", CharsetUtil.UTF_8);
        wrapper.setBuffer(byteBuffer);
        assertEquals("next test", wrapper.toString());
    }
}