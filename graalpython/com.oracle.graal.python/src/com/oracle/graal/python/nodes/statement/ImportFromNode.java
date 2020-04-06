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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AnalysisTags;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.source.SourceSection;

import java.util.Set;

public class ImportFromNode extends AbstractImportNode {
    private final String importee;
    private final int level;
    @CompilationFinal(dimensions = 1) private final String[] fromlist;
    @Children private final WriteNode[] aslist;
    @Child private GetAttributeNode getName;
    @Child private GetItemNode getItem;
    @Child private ReadAttributeFromObjectNode readModules;
    @Child private LookupAndCallBinaryNode getAttributeNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child private LookupAndCallBinaryNode getAttrNode = LookupAndCallBinaryNode.create(__GETATTR__);
    @Child private PRaiseNode raiseNode;
    @CompilationFinal private final IsBuiltinClassProfile getAttributeErrorProfile = IsBuiltinClassProfile.create();
    @CompilationFinal private final IsBuiltinClassProfile getAttrErrorProfile = IsBuiltinClassProfile.create();

    public static ImportFromNode create(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        return new ImportFromNode(importee, fromlist, readNodes, level);
    }

    public String getImportee() {
        return importee;
    }

    public int getLevel() {
        return level;
    }

    public String[] getFromlist() {
        return fromlist;
    }

    protected ImportFromNode(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        this.importee = importee;
        this.fromlist = fromlist;
        this.aslist = readNodes;
        this.level = level;
    }

    private Object readAttributeFromModule(VirtualFrame frame, Object module, String attr) {
        try {
            return getAttributeNode.executeObject(frame, module, attr);
        } catch (PException pe) {
            pe.expectAttributeError(getAttributeErrorProfile);
            return getAttrNode.executeObject(frame, module, attr);
        }
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    public void executeVoid(VirtualFrame frame) {
        Object globals = PArguments.getGlobals(frame);
        Object importedModule = importModule(frame, importee, globals, fromlist, level);
        for (int i = 0; i < fromlist.length; i++) {
            String attr = fromlist[i];
            WriteNode writeNode = aslist[i];
            try {
                writeNode.doWrite(frame, readAttributeFromModule(frame, importedModule, attr));
            } catch (PException pe) {
                pe.expectAttributeError(getAttrErrorProfile);
                if (getName == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getName = insert(GetAttributeNode.create(__NAME__, null));
                }
                try {
                    String pkgname;
                    Object pkgname_o = getName.executeObject(frame, importedModule);
                    if (pkgname_o instanceof PString) {
                        pkgname = ((PString) pkgname_o).getValue();
                    } else if (pkgname_o instanceof String) {
                        pkgname = (String) pkgname_o;
                    } else {
                        throw pe;
                    }
                    String fullname = pkgname + "." + attr;
                    if (getItem == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        getItem = insert(GetItemNode.create());
                        readModules = insert(ReadAttributeFromObjectNode.create());
                    }
                    Object sysModules = readModules.execute(getContext().getCore().lookupBuiltinModule("sys"), "modules");
                    writeNode.doWrite(frame, getItem.execute(frame, sysModules, fullname));
                } catch (PException e2) {
                    if (raiseNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        raiseNode = insert(PRaiseNode.create());
                    }
                    throw raiseNode.raise(ImportError, "cannot import name '%s'", attr);
                }
            }
        }
    }

    @Override
    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        // the materialized node provides the symbols to be imported as intermediate values, the module to import from as the callee
        for (Class<? extends Tag> tag : materializedTags) {
            if (hasTag(tag)) {
                final MaterializedImportFromNode materializedNode = new MaterializedImportFromNode(importee, fromlist, aslist, level);
                materializedNode.assignSourceSection(getSourceSection());
                return materializedNode;
            }
        }
        return this;
    }

    @Override
    public Object getNodeObject() {
        return NodeObjectDescriptor.createNodeObjectDescriptor(AnalysisTags.FunctionCallTag.METADATA_KEY_IS_PREFIX_CALLING, true, AnalysisTags.FunctionCallTag.METADATA_KEY_ARG_OFFSET, 1, AnalysisTags.FunctionCallTag.METADATA_KEY_ARGUMENT_COUNT, fromlist.length, AnalysisTags.FunctionCallTag.METADATA_KEY_CALLEE_NAME, importee);
    }

    private final class MaterializedImportFromNode extends ImportFromNode {

        @Child private ExpressionNode importeeNode;
        @Children private ExpressionNode[] fromNodes;

        protected MaterializedImportFromNode(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
            super(importee, fromlist, readNodes, level);
            importeeNode = new StringLiteralNode(importee);
            fromNodes = new ExpressionNode[fromlist.length];
            for (int i = 0; i < fromlist.length; i++) {
                fromNodes[i] = new StringLiteralNode(fromlist[i]);
            }
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            importeeNode.execute(frame);
            executeFromNodes(frame);
            super.executeVoid(frame);
        }

        @ExplodeLoop
        private void executeFromNodes(VirtualFrame frame) {
            for (ExpressionNode fromNode : fromNodes) {
                fromNode.execute(frame);
            }
        }

        @Override
        public void assignSourceSection(SourceSection source) {
            super.assignSourceSection(source);
            importeeNode.assignSourceSection(source);
            for (int i = 0; i < fromlist.length; i++) {
                fromNodes[i].assignSourceSection(source);
            }
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            return this;
        }
    }
}
