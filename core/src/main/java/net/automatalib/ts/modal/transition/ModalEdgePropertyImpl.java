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
package net.automatalib.ts.modal.transition;

import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;

public class ModalEdgePropertyImpl implements MutableModalEdgeProperty {

    private ModalType modalType;

    public ModalEdgePropertyImpl(ModalType modalType) {
        this.modalType = modalType;
    }

    @Override
    public ModalType getModalType() {
        return this.modalType;
    }

    @Override
    public void setModalType(ModalType type) {
        this.modalType = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ModalEdgePropertyImpl that = (ModalEdgePropertyImpl) o;

        return this.modalType == that.modalType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modalType);
    }
}
