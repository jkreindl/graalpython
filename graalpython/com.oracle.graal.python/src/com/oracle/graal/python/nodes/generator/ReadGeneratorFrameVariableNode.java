/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadVariableNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AnalysisTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.profiles.ValueProfile;

public final class ReadGeneratorFrameVariableNode extends ReadVariableNode {

    private final ValueProfile frameProfile = ValueProfile.createClassProfile();

    private ReadGeneratorFrameVariableNode(FrameSlot slot) {
        super(slot);
    }

    public static ReadGeneratorFrameVariableNode create(FrameSlot slot) {
        return new ReadGeneratorFrameVariableNode(slot);
    }

    @Override
    protected Frame getAccessingFrame(VirtualFrame frame) {
        return frameProfile.profile(PArguments.getGeneratorFrame(frame));
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return WriteGeneratorFrameVariableNode.create(frameSlot, rhs);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return StandardTags.ReadVariableTag.class == tag || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(StandardTags.ReadVariableTag.NAME, getSlot().getIdentifier(), AnalysisTags.ReadVariableTag.METADATA_KEY_NAME, getSlot().getIdentifier());
    }
}
