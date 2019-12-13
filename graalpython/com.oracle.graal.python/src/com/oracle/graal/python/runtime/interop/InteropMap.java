/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.runtime.interop;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * A container class used to store per-node attributes used by the instrumentation framework.
 *
 */
@ExportLibrary(InteropLibrary.class)
public final class InteropMap implements TruffleObject {
    private final Map<String, Object> data;

    public InteropMap(Map<String, Object> data) {
        this.data = data;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage(name = "readMember")
    @TruffleBoundary
    Object getKey(String name) throws UnknownIdentifierException {
        if (!hasKey(name)) {
            throw UnknownIdentifierException.create(name);
        }
        return data.get(name);
    }

    @ExportMessage(name = "isMemberReadable")
    @TruffleBoundary
    boolean hasKey(String name) {
        return data.containsKey(name);
    }

    @ExportMessage(name = "getMembers")
    @TruffleBoundary
    TruffleObject getKeys(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(data.keySet().toArray());
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return data.keySet().stream().map(key -> String.format("\"%s\": %s", key, toString(data.get(key)))).collect(Collectors.joining(", ", "{", "}"));
    }

    @TruffleBoundary
    private static String toString(Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        } else {
            return String.format("\"%s\"", value);
        }
    }

    @TruffleBoundary
    public static InteropMap fromPDict(PDict dict) {
        Map<String, Object> map = new HashMap<>();
        for (DictEntry s : dict.getDictStorage().entries()) {
            map.put(s.getKey().toString(), s.getValue());
        }
        return new InteropMap(map);
    }

    @TruffleBoundary
    public static InteropMap fromPythonObject(PythonObject globals) {
        Map<String, Object> map = new HashMap<>();
        for (String name : globals.getAttributeNames()) {
            map.put(name, globals.getAttribute(name));
        }
        return new InteropMap(map);
    }

    @TruffleBoundary
    public static InteropMap create(String key, Object value) {
        return new InteropMap(Collections.singletonMap(key, value));
    }

    @TruffleBoundary
    public static InteropMap create(String key1, Object value1, String key2, Object value2) {
        final HashMap<String, Object> data = new HashMap<>();
        data.put(key1, value1);
        data.put(key2, value2);
        return new InteropMap(data);
    }

    @TruffleBoundary
    public static InteropMap create(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        final HashMap<String, Object> data = new HashMap<>();
        data.put(key1, value1);
        data.put(key2, value2);
        data.put(key3, value3);
        return new InteropMap(data);
    }

    @TruffleBoundary
    public static InteropMap create(String key1, Object value1, String key2, Object value2, String key3, Object value3, String key4, Object value4) {
        final HashMap<String, Object> data = new HashMap<>();
        data.put(key1, value1);
        data.put(key2, value2);
        data.put(key3, value3);
        data.put(key4, value4);
        return new InteropMap(data);
    }
}
