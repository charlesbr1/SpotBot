package org.sbot.utils;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;


public final class ByteBufferWrapper implements CharSequence {

    private ByteBuf byteBuffer;

    @NotNull
    public ByteBuf buffer() {
        return byteBuffer;
    }

    public void setBuffer(@NotNull ByteBuf byteBuffer) {
        this.byteBuffer = requireNonNull(byteBuffer);
    }

    @Override
    public int length() {
        return byteBuffer.readableBytes();
    }

    @Override
    public char charAt(int index) {
        return (char) byteBuffer.getByte(index);
    }

    @NotNull
    @Override
    public CharSequence subSequence(int start, int end) {
        if(end == start) {
            return "";
        }
        var wrapper = new ByteBufferWrapper();
        wrapper.setBuffer(byteBuffer.slice(start, end - start));
        return wrapper;
    }

    @NotNull
    @Override
    public String toString() {
        return byteBuffer.toString(CharsetUtil.UTF_8);
    }
}
