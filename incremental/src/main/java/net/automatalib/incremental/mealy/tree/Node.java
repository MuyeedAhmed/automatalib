/* Copyright (C) 2013-2022 TU Dortmund
 * This file is part of AutomataLib, http://www.automatalib.net/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.automatalib.incremental.mealy.tree;

import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import org.checkerframework.checker.nullness.qual.Nullable;

final class Node<O> {

    private final ResizingArrayStorage<Edge<Node<O>, O>> outEdges;

    Node(int alphabetSize) {
        this.outEdges = new ResizingArrayStorage<>(Edge.class, alphabetSize);
    }

    Edge<Node<O>, O> getEdge(int idx) {
        return outEdges.array[idx];
    }

    void setEdge(int idx, Edge<Node<O>, O> edge) {
        outEdges.array[idx] = edge;
    }

    Node<O> getSuccessor(int idx) {
        Edge<Node<O>, O> edge = outEdges.array[idx];
        if (edge != null) {
            return edge.getTarget();
        }
        return null;
    }

    /**
     * See {@link ResizingArrayStorage#ensureCapacity(int)}.
     */
    boolean ensureInputCapacity(int capacity) {
        return this.outEdges.ensureCapacity(capacity);
    }
}
