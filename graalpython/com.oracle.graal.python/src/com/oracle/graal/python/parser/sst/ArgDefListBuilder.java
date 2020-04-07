/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.ScopeEnvironment;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.truffle.api.source.SourceSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArgDefListBuilder {

    private static final String SPLAT_MARKER_NAME = "*";

    private class Parameter {

        protected final String name;

        // We don't use type now, but in future ...
        @SuppressWarnings("unused") protected final SSTNode type;

        protected final int startOffset;
        protected final int endOffset;

        public Parameter(String name, SSTNode type, int startOffset, int endOffset) {
            this.name = name;
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

    }

    private class ParameterWithDefValue extends Parameter {

        protected final SSTNode value;

        public ParameterWithDefValue(String name, SSTNode type, SSTNode value, int startOffset, int endOffset) {
            super(name, type, startOffset, endOffset);
            this.value = value;
        }

    }

    private final ScopeEnvironment scopeEnvironment;

    private static final ExpressionNode[] EMPTY = new ExpressionNode[0];
    private List<Parameter> args;
    private List<ParameterWithDefValue> argsWithDefValue;
    private List<Parameter> kwargs;
    private List<ParameterWithDefValue> kwargsWithDefValue;
    private final Set<String> paramNames;
    private int splatIndex = -1;
    private int kwarIndex = -1;
    private int positionalOnlyIndex = -1;  // index to the last positional argument
    private int countOfTypedParams = 0;

    public ArgDefListBuilder(ScopeEnvironment scopeEnvironment) {
        this.paramNames = new HashSet<>();
        this.scopeEnvironment = scopeEnvironment;
    }

    public static enum AddParamResult {
        OK, // was added without an error
        NONDEFAULT_FOLLOWS_DEFAULT, // non-default argument follows default argument
        DUPLICATED_ARGUMENT// duplicate argument 'x' in function definition
    };

    public AddParamResult addParam(String name, SSTNode type, SSTNode defValue, int startOffset, int endOffset) {
        if (paramNames.contains(name)) {
            return AddParamResult.DUPLICATED_ARGUMENT;
        }
        if (defValue == null && hasDefaultParameter() && !hasSplat()) {
            return AddParamResult.NONDEFAULT_FOLLOWS_DEFAULT;
        }
        paramNames.add(name);
        Parameter arg = defValue == null ? new Parameter(name, type, startOffset, endOffset) : new ParameterWithDefValue(name, type, defValue, startOffset, endOffset);
        if (type != null) {
            countOfTypedParams++;
        }
        if (splatIndex == -1) {
            if (defValue != null) {
                if (argsWithDefValue == null) {
                    argsWithDefValue = new ArrayList<>(5);
                }
                argsWithDefValue.add((ParameterWithDefValue) arg);
            }
            if (args == null) {
                args = new ArrayList<>();
            }
            args.add(arg);
        } else {
            if (defValue != null) {
                if (kwargsWithDefValue == null) {
                    kwargsWithDefValue = new ArrayList<>(4);
                }
                kwargsWithDefValue.add((ParameterWithDefValue) arg);
            }
            if (kwargs == null) {
                kwargs = new ArrayList<>(4);
            }
            kwargs.add(arg);
        }
        return AddParamResult.OK;
    }

    public void addSplat(String name, SSTNode type, int startOffset, int endOffset) {
        // System.out.println("Splat: " + name);
        if (args == null) {
            args = new ArrayList<>();
        }
        args.add(new Parameter(name, type, startOffset, endOffset));
        splatIndex = args.size() - 1;
    }

    public void addKwargs(String name, SSTNode type, int startOffset, int endOffset) {
        // System.out.println("KwArg: " + name);
        if (kwargs == null) {
            kwargs = new ArrayList<>();
        }
        kwargs.add(new Parameter(name, type, startOffset, endOffset));
        kwarIndex = kwargs.size() - 1;
    }

    public boolean hasDefaultParameter() {
        return argsWithDefValue != null;
    }

    public void defineParamsInScope(ScopeInfo functionScope) {
        if (args != null) {
            for (Parameter param : args) {
                if (param.name != null) {
                    // don't put splat marker
                    functionScope.createSlotIfNotPresent(param.name);
                } else {
                    functionScope.createSlotIfNotPresent(SPLAT_MARKER_NAME);
                }
            }
        }
        if (kwargs != null) {
            for (Parameter param : kwargs) {
                functionScope.createSlotIfNotPresent(param.name);
            }
        }
    }

    public boolean hasSplatStarMarker() {
        return splatIndex > -1 && args != null && args.get(splatIndex).name == null;
    }

    public boolean hasSplat() {
        return splatIndex > -1;
    }

    /**
     * 
     * @return The index to the positional only argument marker ('/'). Which means that all
     *         positional only argument have index smaller then this.
     */
    public int getPositionalOnlyIndex() {
        return positionalOnlyIndex;
    }

    public void markPositionalOnlyIndex() {
        this.positionalOnlyIndex = args.size();
    }

    public interface SourceProducer {
        SourceSection createSourceSection(int startOffset, int endOffset);
    }

    private StatementNode getArgumentWrite(Parameter parameter, ReadArgumentNode readArgumentNode, SourceProducer sourceProducer) {
        readArgumentNode.assignSourceSection(sourceProducer.createSourceSection(parameter.startOffset, parameter.endOffset));
        final StatementNode writeNode = scopeEnvironment.getWriteNode(parameter.name, readArgumentNode);
        writeNode.assignSourceSection(sourceProducer.createSourceSection(parameter.startOffset, parameter.endOffset));
        return writeNode;
    }

    public StatementNode getWriteArgumentToLocal(Parameter parameter, int index, SourceProducer sourceProducer) {
        return getArgumentWrite(parameter, scopeEnvironment.getIndexedArgumentRead(index), sourceProducer);
    }

    public StatementNode getWriteVarArgsToLocal(Parameter parameter, int index, SourceProducer sourceProducer) {
        return getArgumentWrite(parameter, scopeEnvironment.getIndexedVarArgRead(index), sourceProducer);
    }

    public StatementNode getWriteKwArgsToLocal(Parameter parameter, String[] kwId, SourceProducer sourceProducer) {
        return getArgumentWrite(parameter, scopeEnvironment.getKwArgsRead(kwId), sourceProducer);
    }

    public StatementNode[] getArgumentNodes(SourceProducer sourceProducer) {
        assert sourceProducer != null;
        if (args == null && kwargs == null) {
            return new StatementNode[0];
        }
        boolean starMarker = hasSplatStarMarker();
        int delta = starMarker ? 1 : 0;
        int argsLen = args == null ? 0 : args.size();
        int kwargsLen = kwargs == null ? 0 : kwargs.size();
        StatementNode[] nodes = new StatementNode[argsLen + kwargsLen - delta];

        for (int i = 0; i < argsLen - delta; i++) {
            if (splatIndex == i) {
                if (!starMarker) {
                    nodes[i] = getWriteVarArgsToLocal(args.get(i), i, sourceProducer);
                }
            } else {
                nodes[i] = getWriteArgumentToLocal(args.get(i), i, sourceProducer);
            }
        }

        String[] kwId = kwarIndex == -1 ? new String[0] : new String[kwargsLen - 1];
        delta = argsLen - delta;
        int starMarkerDelta = starMarker ? 0 : 1;
        for (int i = 0; i < kwargsLen; i++) {
            if (i != kwarIndex) {
                nodes[i + delta] = getWriteArgumentToLocal(kwargs.get(i), i + delta - starMarkerDelta, sourceProducer);
                if (kwarIndex != -1) {
                    kwId[i] = kwargs.get(i).name;
                }
            } else {
                nodes[i + delta] = getWriteKwArgsToLocal(kwargs.get(i), kwId, sourceProducer);
            }
        }
        return nodes;
    }

    public Map<String, SSTNode> getAnnotatedArgs() {
        if (countOfTypedParams == 0) {
            return null;
        }
        Map<String, SSTNode> result = new HashMap<>(countOfTypedParams);
        for (Parameter param : args) {
            if (param.type != null) {
                result.put(param.name, param.type);
            }
        }

        return result;
    }

    public ExpressionNode[] getDefaultParameterValues(FactorySSTVisitor visitor) {
        if (argsWithDefValue == null) {
            return EMPTY;
        }
        ExpressionNode[] nodes = new ExpressionNode[argsWithDefValue.size()];
        for (int i = 0; i < argsWithDefValue.size(); i++) {
            nodes[i] = (ExpressionNode) argsWithDefValue.get(i).value.accept(visitor);
        }
        return nodes;
    }

    public FunctionDefinitionNode.KwDefaultExpressionNode[] getKwDefaultParameterValues(FactorySSTVisitor visitor) {
        if (kwargsWithDefValue == null) {
            return null;
        }
        FunctionDefinitionNode.KwDefaultExpressionNode[] nodes = new FunctionDefinitionNode.KwDefaultExpressionNode[kwargsWithDefValue.size()];
        for (int i = 0; i < kwargsWithDefValue.size(); i++) {
            nodes[i] = FunctionDefinitionNode.KwDefaultExpressionNode.create(kwargsWithDefValue.get(i).name, (ExpressionNode) kwargsWithDefValue.get(i).value.accept(visitor));
        }
        return nodes;
    }

    public Signature getSignature() {
        if (args == null && kwargs == null) {
            return Signature.EMPTY;
        }
        int i;
        String[] ids = null;
        String[] kwids = null;
        boolean splatMarker = hasSplatStarMarker();
        if (args != null) {
            ids = new String[args.size() - (splatIndex == -1 ? 0 : 1)];
            i = 0;
            if (ids.length > 0) {
                for (Parameter param : args) {
                    if (i != splatIndex) {
                        ids[i++] = param.name;
                    }
                }
            }
        }
        if (kwargs != null) {
            kwids = new String[kwargs.size() - (kwarIndex == -1 ? 0 : 1)];
            i = 0;
            if (kwids.length > 0) {
                for (Parameter param : kwargs) {
                    if (i != kwarIndex) {
                        kwids[i++] = param.name;
                    }
                }
            }
        }
        return new Signature(positionalOnlyIndex, kwarIndex > -1, splatMarker ? -1 : splatIndex, splatMarker, ids, kwids);
    }

}
