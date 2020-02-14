/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNodeGen.WriteLocalFrameSlotNodeGen;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AnalysisTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "write_local")
@NodeChild(value = "rightNode", type = ExpressionNode.class)
public abstract class WriteLocalVariableNode extends StatementNode implements WriteIdentifierNode {
    @Child private WriteLocalFrameSlotNode writeNode;

    public abstract static class WriteLocalFrameSlotNode extends FrameSlotNode {
        public WriteLocalFrameSlotNode(FrameSlot slot) {
            super(slot);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final int executeInt(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final long executeLong(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final double executeDouble(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final boolean executeBoolean(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        public abstract Object executeWith(VirtualFrame frame, boolean value);

        public abstract Object executeWith(VirtualFrame frame, int value);

        public abstract Object executeWith(VirtualFrame frame, long value);

        public abstract Object executeWith(VirtualFrame frame, double value);

        public abstract Object executeWith(VirtualFrame frame, Object value);

        @Specialization(guards = "isBooleanKind(frame)")
        public boolean write(VirtualFrame frame, boolean value) {
            frame.setBoolean(frameSlot, value);
            return value;
        }

        @Specialization(guards = "isIntegerKind(frame)")
        public int write(VirtualFrame frame, int value) {
            frame.setInt(frameSlot, value);
            return value;
        }

        @Specialization(guards = {"isPrimitiveInt(value)", "!value.isNative()", "isLongKind(frame)"}, rewriteOn = ArithmeticException.class)
        public long writeSmallPIntAsLong(VirtualFrame frame, PInt value) {
            long longValue = value.longValueExact();
            frame.setLong(frameSlot, longValue);
            return longValue;
        }

        @Specialization(guards = {"isPrimitiveInt(value)", "!value.isNative()"}, rewriteOn = ArithmeticException.class)
        public long writeSmallPIntAsObject(VirtualFrame frame, PInt value) {
            ensureObjectKind(frame);
            long longValue = value.longValueExact();
            frame.setObject(frameSlot, longValue);
            return longValue;
        }

        @Specialization(guards = "isDoubleKind(frame)")
        public double write(VirtualFrame frame, double value) {
            frame.setDouble(frameSlot, value);
            return value;
        }

        @Specialization
        public Object write(VirtualFrame frame, Object value) {
            ensureObjectKind(frame);
            frame.setObject(frameSlot, value);
            return value;
        }
    }

    public WriteLocalVariableNode(FrameSlot slot) {
        this.writeNode = WriteLocalFrameSlotNodeGen.create(slot);
    }

    public static WriteLocalVariableNode create(FrameSlot slot, ExpressionNode right) {
        return WriteLocalVariableNodeGen.create(slot, right);
    }

    public abstract ExpressionNode getRightNode();

    @Override
    public ExpressionNode getRhs() {
        return getRightNode();
    }

    @Override
    public Object getIdentifier() {
        return writeNode.getSlot().getIdentifier();
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, boolean value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, int value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, long value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, double value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, Object value) {
        writeNode.executeWith(frame, value);
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }

    public final FrameSlot getSlot() {
        return writeNode.getSlot();
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == AnalysisTags.ReadVariableTag.class || super.hasTag(tag);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(AnalysisTags.ReadVariableTag.METADATA_KEY_NAME, String.valueOf(getSlot()));
    }
}
