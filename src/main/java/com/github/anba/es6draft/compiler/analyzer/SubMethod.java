/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler.analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.anba.es6draft.ast.Node;
import com.github.anba.es6draft.ast.StatementListItem;

/**
 * Base class for node re-writing classesF
 */
abstract class SubMethod<NODE extends Node> {
    protected static final int MAX_SIZE = 32768;

    protected static final int MAX_STATEMENT_SIZE = 8192;
    protected static final int MAX_EXPR_SIZE = 1024;

    protected static final int STMT_METHOD_SIZE = 15;
    protected static final int EXPR_METHOD_SIZE = 5;

    protected SubMethod() {
    }

    /**
     * Process {@code node} and return the new size
     */
    abstract int processNode(NODE node, int oldSize);

    protected void abortCompilation(int size) {
        throw new CodeSizeException(size);
    }

    protected int validateSize(NODE node) {
        CodeSizeVisitor visitor = new CodeSizeVisitor();
        int newSize = visitor.startAnalyze(node, new EmptyHandler());
        if (newSize >= MAX_SIZE) {
            abortCompilation(newSize);
        }
        return newSize;
    }

    protected int validateSize(NODE node, List<? extends Node> children) {
        CodeSizeVisitor visitor = new CodeSizeVisitor();
        int newSize = visitor.startAnalyze(node, children, new EmptyHandler());
        if (newSize >= MAX_SIZE) {
            abortCompilation(newSize);
        }
        return newSize;
    }

    protected static class NodeElement<NODE extends Node> implements Comparable<NodeElement<?>> {
        NODE node;
        int index;
        int size;

        NodeElement(NODE node, int index, int size) {
            this.node = node;
            this.index = index;
            this.size = size;
        }

        @Override
        public int compareTo(NodeElement<?> o) {
            return o.size - size;
        }
    }

    protected static abstract class Conflater<Source, Target> {
        protected abstract int getSize(Source source);

        protected abstract Target newTarget(List<Target> list);

        private void conflate(List<Target> newElements, int start, int end) {
            List<Target> view = newElements.subList(start, end);
            Target chunk = newTarget(new ArrayList<>(view));
            view.clear();
            newElements.add(start, chunk);
        }

        void conflate(List<Source> sourceElements, List<Target> targetElements, final int maxSize) {
            int chunkSize = 0, end = sourceElements.size();
            for (int i = sourceElements.size() - 1; i >= 0; --i) {
                Source source = sourceElements.get(i);
                int size = getSize(source);
                if (chunkSize + size < maxSize) {
                    chunkSize += size;
                } else {
                    // insert new chunk
                    int start = i + 1;
                    if (start < end) {
                        conflate(targetElements, start, end);
                    }

                    chunkSize = size;
                    end = start;
                }
            }

            if (chunkSize > maxSize) {
                conflate(targetElements, 0, end);
            }
        }
    }

    /**
     * {@link CodeSizeHandler} which performs no further action
     */
    protected static class EmptyHandler implements CodeSizeHandler {
        @Override
        public int reportSize(Node node, int size) {
            return size;
        }

        @Override
        public void submit(Node node, List<? extends Node> children) {
        }
    }

    /**
     * {@link CodeSizeHandler} which saves code size for further re-use
     */
    protected static class MemorizingHandler implements CodeSizeHandler {
        Map<StatementListItem, Integer> codeSizes = new HashMap<>();

        @Override
        public int reportSize(Node node, int size) {
            if (node instanceof StatementListItem) {
                codeSizes.put((StatementListItem) node, size);
            }
            return size;
        }

        @Override
        public void submit(Node node, List<? extends Node> children) {
        }
    }
}