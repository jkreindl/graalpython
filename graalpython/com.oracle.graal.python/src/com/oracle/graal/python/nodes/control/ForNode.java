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
package com.oracle.graal.python.nodes.control;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongIterator;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AnalysisTags;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.source.SourceSection;

final class ForRepeatingNode extends PNodeWithContext implements RepeatingNode {

    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @Child ForNextElementNode nextElement;
    @Child StatementNode body;

    public ForRepeatingNode(StatementNode target, StatementNode body, final FrameSlot iteratorSlot) {
        this.nextElement = ForNextElementNodeGen.create(target, ReadLocalVariableNode.create(iteratorSlot));
        this.body = body;
    }

    public boolean executeRepeating(VirtualFrame frame) {
        if (!nextElement.execute(frame)) {
            return false;
        }
        body.executeVoid(frame);
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        contextRef.get().triggerAsyncActions(frame, this);
        return true;
    }

    void assignSourceSection(SourceSection sourceSection) {
        nextElement.assignSourceSection(sourceSection);
    }
}

@ImportStatic({PythonOptions.class, SpecialMethodNames.class})
@NodeChild(value = "iterator", type = ExpressionNode.class)
@GenerateWrapper
abstract class ForNextElementNode extends PNodeWithContext implements InstrumentableNode {

    @Child StatementNode target;

    private SourceSection sourceSection;

    public ForNextElementNode(StatementNode target) {
        this.target = target;
    }

    public ForNextElementNode(ForNextElementNode other) {
        this((StatementNode) null);
    }

    public abstract boolean execute(VirtualFrame frame);

    public abstract ExpressionNode getIterator();

    /*
     * There's a limited number of iterator types - specialize to all of them.
     */

    @Specialization(guards = "iterator.getClass() == clazz", limit = "99")
    protected boolean doIntegerIterator(VirtualFrame frame, PIntegerIterator iterator,
                    @Cached("iterator.getClass()") Class<? extends PIntegerIterator> clazz) {
        PIntegerIterator profiledIterator = clazz.cast(iterator);
        if (!profiledIterator.hasNext()) {
            profiledIterator.setExhausted();
            return false;
        }
        ((WriteNode) target).doWrite(frame, profiledIterator.next());
        return true;
    }

    @Specialization(guards = "iterator.getClass() == clazz", limit = "99")
    protected boolean doLongIterator(VirtualFrame frame, PLongIterator iterator,
                    @Cached("iterator.getClass()") Class<? extends PLongIterator> clazz) {
        PLongIterator profiledIterator = clazz.cast(iterator);
        if (!profiledIterator.hasNext()) {
            profiledIterator.setExhausted();
            return false;
        }
        ((WriteNode) target).doWrite(frame, profiledIterator.next());
        return true;
    }

    @Specialization(guards = "iterator.getClass() == clazz", limit = "99")
    protected boolean doDoubleIterator(VirtualFrame frame, PDoubleIterator iterator,
                    @Cached("iterator.getClass()") Class<? extends PDoubleIterator> clazz) {
        PDoubleIterator profiledIterator = clazz.cast(iterator);
        if (!profiledIterator.hasNext()) {
            profiledIterator.setExhausted();
            return false;
        }
        ((WriteNode) target).doWrite(frame, profiledIterator.next());
        return true;
    }

    @Specialization
    protected boolean doIterator(VirtualFrame frame, Object object,
                    @Cached("create()") GetNextNode next,
                    @Cached("create()") IsBuiltinClassProfile errorProfile) {
        try {
            ((WriteNode) target).doWrite(frame, next.execute(frame, object));
            return true;
        } catch (PException e) {
            e.expectStopIteration(errorProfile);
            return false;
        }
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return target.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return target.getNodeObject();
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new ForNextElementNodeWrapper(this, this, probe);
    }

    void assignSourceSection(SourceSection sourceSection) {
        // instrument this node instead the target so that the input event
        // from reading the iterator has a proper parent
        this.sourceSection = sourceSection;
        target.clearSourceSection();
        getIterator().assignSourceSection(sourceSection);
    }
}

@NodeInfo(shortName = "for")
public final class ForNode extends LoopNode implements InstrumentableControlFlow {

    private final FrameSlot iteratorSlot;

    @Child private com.oracle.truffle.api.nodes.LoopNode loopNode;
    @Child private WriteLocalVariableNode setIteratorNode;

    public ForNode(StatementNode body, StatementNode target, ExpressionNode iterator, FrameSlot iteratorSlot) {
        final ForRepeatingNode repeatingNode = new ForRepeatingNode(target, body, iteratorSlot);
        this.loopNode = Truffle.getRuntime().createLoopNode(repeatingNode);
        this.iteratorSlot = iteratorSlot;
        setIteratorNode = WriteLocalVariableNode.create(iteratorSlot, iterator);
    }

    public StatementNode getTarget() {
        return ((ForRepeatingNode) loopNode.getRepeatingNode()).nextElement.target;
    }

    public ExpressionNode getIterator() {
        return setIteratorNode.getRhs();
    }

    @Override
    public StatementNode getBody() {
        return ((ForRepeatingNode) loopNode.getRepeatingNode()).body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        setIteratorNode.executeVoid(frame);
        try {
            loopNode.execute(frame);
        } finally {
            frame.setObject(iteratorSlot, null);
        }
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == AnalysisTags.ControlFlowBranchTag.class || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(AnalysisTags.ControlFlowBranchTag.METADATA_KEY_TYPE, AnalysisTags.ControlFlowBranchTag.Type.Loop.name());
    }

    @Override
    public void assignSourceSection(SourceSection source) {
        // instrument the write to the iterator slot
        setIteratorNode.assignSourceSection(source);

        // instrument the update of the loop variable
        getTarget().assignSourceSection(source);

        // instrument the read of the iterator to update the loop variable
        ((ForRepeatingNode) loopNode.getRepeatingNode()).assignSourceSection(source);
    }
}
