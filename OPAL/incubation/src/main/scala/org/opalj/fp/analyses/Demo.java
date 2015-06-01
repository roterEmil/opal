/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj.fp.analyses;

class Demo {

    private Demo() {/* empty */
    }

    public static int simplyPure(int i, int j) {
        return i % 3 == 0 ? i : j;
    }

    public static int impure(int i) {
        return (int) (i * System.nanoTime());
    }

    //
    // Both methods are actually pure but have a dependency on each other...
    //
    static int foo(int i) {
        return i < 0 ? i : bar(i - 10);
    }

    static int bar(int i) {
        return i % 2 == 0 ? i : foo(i - 1);
    }
    
    // the following method is (conditionally) pure, but does not contribute to the 
    // cyclic computation

    static int fooBar(int i) {
        return foo(i)+bar(i);
    }
    
    //
    // All three methods are actually pure but have a dependency on each other...
    //
    static int m1(int i) {
        return i < 0 ? i : m2(i - 10);
    }

    static int m2(int i) {
        return i % 2 == 0 ? i : m3(i - 1);
    }

    static int m3(int i) {
        return i % 4 == 0 ? i : m1(i - 1);
    }

    //
    // The following method is pure, but only if we know the pureness of the target method
    // which we don't know if do not analyze the JDK!
    //
    
    static int cpure(int i) {
        return Math.abs(i) * 21;
    }

    static int cpureCallee(int i) {
        return cpure(i / 21);
    }

}
