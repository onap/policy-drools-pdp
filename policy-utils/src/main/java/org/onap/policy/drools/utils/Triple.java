/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

public class Triple<F,S,T> {

    private F first;
    private S second;
    private T third;

    public Triple(F first, S second, T third){
        this.first = first;
        this.second = second;
        this.third = third;
    }
    public F first(){ return this.first; }

    public S second(){ return this.second; }

    public T third(){ return this.third; }

    public void first(F first){ this.first = first; }

    public void second(S second){ this.second = second; }

    public void third(T third){ this.third = third; }
}
