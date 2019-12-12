package com.oracle.graal.python.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A container class used to store per-node attributes used by the instrumentation framework.
 *
 */
@ExportLibrary(InteropLibrary.class)
public final class PNodeObject implements TruffleObject {

    @TruffleBoundary
    public static PNodeObject create(String key, Object value) {
        return new PNodeObject(Collections.singletonMap(key, value));
    }

    @TruffleBoundary
    public static PNodeObject create(String key1, Object value1, String key2, Object value2) {
        final HashMap<String, Object> data = new HashMap<>();
        data.put(key1, value1);
        data.put(key2, value2);
        return new PNodeObject(data);
    }

    private final Map<String, Object> data;

    public PNodeObject(Map<String, Object> data) {
        this.data = data;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String key) {
        assert data.containsKey(key);
        return data.get(key);
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PNodeObjectKeys(data);
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String key) {
        return data.containsKey(key);
    }

    @ExportLibrary(InteropLibrary.class)
    public final class PNodeObjectKeys implements TruffleObject {

        private final Object[] keys;

        PNodeObjectKeys(Map<String, Object> from) {
            this.keys = from.keySet().toArray();
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            return keys[(int) index];
        }
    }
}
