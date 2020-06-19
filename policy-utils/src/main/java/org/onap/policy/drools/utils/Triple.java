/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.utils;

public class Triple<F, S, T> {

    private F first;
    private S second;
    private T third;

    public Triple() {
        // empty constructor
    }

    /**
     * Constructor.
     *
     * @param first first
     * @param second second
     * @param third third
     */
    public Triple(F first, S second, T third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public F first() {
        return this.getFirst();
    }

    public void first(F first) {
        this.setFirst(first);
    }

    public F getFirst() {
        return first;
    }

    public void setFirst(F first) {
        this.first = first;
    }

    public S second() {
        return this.getSecond();
    }

    public void second(S second) {
        this.setSecond(second);
    }

    public S getSecond() {
        return second;
    }

    public void setSecond(S second) {
        this.second = second;
    }

    public T third() {
        return this.getThird();
    }

    public void third(T third) {
        this.setThird(third);
    }

    public T getThird() {
        return this.third;
    }

    public void setThird(T third) {
        this.third = third;
    }
}
