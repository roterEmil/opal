/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
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
package org.opalj.fpcf.fixtures.purity;

import org.opalj.fpcf.analyses.L0PurityAnalysis;
import org.opalj.fpcf.analyses.L2PurityAnalysis;
import org.opalj.fpcf.analyses.L1PurityAnalysis;
import org.opalj.fpcf.properties.purity.EP;
import org.opalj.fpcf.properties.purity.Impure;
import org.opalj.fpcf.properties.purity.Pure;

/**
 * Some Demo code to test/demonstrate the complexity related to calculating the purity of
 * methods in the presence of mutual recursive methods.
 *
 * @author Michael Eichberg
 * @author Dominik Helm
 */
final class DependentCalls { // This class is immutable

    private static int myValue =
            -1; /* the FieldMutabilityAnalysis is required to determine that this field is effectivelyFinal  */

    @Pure(value = "nothing done here",
            eps = @EP(cf = Object.class, method = "<init>()V", pk = "Purity",
                    p = "LBPure", analyses = L0PurityAnalysis.class))
    @Impure(value = "Object.init<> not recognized as pure",
            eps = @EP(cf = Object.class, method = "<init>()V", pk = "Purity",
                    p = "LBPure"),
            negate = true, analyses = L0PurityAnalysis.class)
    private DependentCalls() {
        /* empty */
    }

    @Pure(value = "only constructs an object of this immutable class",
            analyses = { L1PurityAnalysis.class,
                    L2PurityAnalysis.class })
    @Impure(value = "Instantiates new object", analyses = L0PurityAnalysis.class)
    public static DependentCalls createDependentCalls() {
        return new DependentCalls();
    }

    @Pure(value = "object returned is immutable",
            eps = @EP(cf = DependentCalls.class, pk = "ClassImmutability", p = "ImmutableObject"))
    @Impure(value = "object returend not recognized as immutable",
            eps = @EP(cf = DependentCalls.class, pk = "ClassImmutability", p = "ImmutableObject"),
            negate = true, analyses = L1PurityAnalysis.class)
    public DependentCalls pureIdentity() {
        return this;
    }

    @Pure(value = "field used is effectively final",
            eps = @EP(cf = DependentCalls.class, field = "myValue", pk = "FieldMutability",
                    p = "EffectivelyFinalField"))
    @Impure(value = "field used not recognized as effectively final",
            eps = @EP(cf = DependentCalls.class, field = "myValue", pk = "FieldMutability",
                    p = "EffectivelyFinalField"),
            negate = true, analyses = L0PurityAnalysis.class)
    public static int pureUsesEffectivelyFinalField(int i, int j) {
        return i * j * myValue;
    }

    @Pure("only calls itself recursively")
    public static int pureSimpleRecursiveCall(int i, int j) {
        return i == 0 ? pureSimpleRecursiveCall(i, 0) : pureSimpleRecursiveCall(0, j);
    }

    @Impure("calls native function System.nanoTime()")
    public static int impureCallsSystemFunction(int i) {
        return (int) (i * System.getenv().size());
    }

    // --------------------------------------------------------------------------------------------
    // The following two methods are mutually dependent and are pure.
    //
    @Pure("function called is pure")
    static int pureMutualRecursiveCall1(int i) {
        return i < 0 ? i : pureMutualRecursiveCall2(i - 10);
    }

    @Pure("function called is pure")
    static int pureMutualRecursiveCall2(int i) {
        return i == 0 ? i : pureMutualRecursiveCall1(i - 1);
    }

    // --------------------------------------------------------------------------------------------
    // The following methods are not directly involved in a  mutually recursive dependency, but
    // require information about a set of mutually recursive dependent methods.

    @Pure("functions called are pure")
    static int pureCallsMutuallyRecursivePureMethods(int i) { // also observed by other methods
        return pureMutualRecursiveCall1(i) + pureMutualRecursiveCall2(i);
    }

    @Pure("functions called are pure")
    static int pureUnusedCallsMutuallyRecursivePureMethods(int i) {
        return pureMutualRecursiveCall1(i) + pureMutualRecursiveCall2(i);
    }

    // --------------------------------------------------------------------------------------------
    // The following two methods are mutually dependent and use an impure method.
    //

    @Impure("transitively calls native function")
    static int impureMutuallyRecursiveCallCallsImpure1(int i) {
        return i < 0 ?
                pureSimpleRecursiveCall(i, 0) :
                impureMutuallyRecursiveCallCallsImpure2(i - 10);
    }

    @Impure("transitively calls native function")
    static int impureMutuallyRecursiveCallCallsImpure2(int i) {
        return i % 2 == 0 ?
                impureCallsSystemFunction(i) :
                impureMutuallyRecursiveCallCallsImpure1(i - 1);
    }

    // --------------------------------------------------------------------------------------------
    // All three methods are actually pure but have a dependency on each other...
    //
    @Pure("methods in cycle are all")
    static int pureCyclicRecursiveCall1(int i) {
        return i < 0 ? i : pureCyclicRecursiveCall2(i - 10);
    }

    @Pure("methods in cycle are all pure")
    static int pureCyclicRecursiveCall2(int i) {
        return i == 0 ? i : pureCyclicRecursiveCall3(i - 1);
    }

    @Pure("methods in cycle are all pure")
    static int pureCyclicRecursiveCall3(int i) {
        return i > 0 ? i : pureCyclicRecursiveCall1(i - 1);
    }

    // --------------------------------------------------------------------------------------------
    // The following methods are pure, but only if we know the pureness of the target method
    // which we don't know if do not analyze the JDK!
    //

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbs(int i) {
        return Math.abs(i) * 21;
    }

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbsCallee(int i) {
        return cpureCallsAbs(i + 21);
    }

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbsCalleeCallee1(int i) {
        return cpureCallsAbsCallee(i - 21);
    }

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbsCalleeCallee2(int i) {
        return cpureCallsAbsCallee(i * 21);
    }

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbsCalleeCalleeCallee(int i) {
        return cpureCallsAbsCalleeCallee1(i + 21) * cpureCallsAbsCalleeCallee2(i - 21);
    }

    @Pure(value = "calls pure Math.abs",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"))
    @Impure(value = "Math.abs not recognized as pure",
            eps = @EP(cf = Math.class, method = "abs(I)I", pk = "Purity", p = "LBPure"),
            negate = true)
    static int cpureCallsAbsCalleeCalleeCalleCallee(int i) {
        return cpureCallsAbsCalleeCalleeCallee(1299);
    }

    // --------------------------------------------------------------------------------------------
    // All methods are involved in multiple cycles of dependent methods; one calls an impure method.
    //

    @Impure("transitively calls native function")
    static int impureRecursiveCallWithDependency1(int i) {
        return i < 0 ? i : impureRecursiveCallWithDependency2(i - 10);
    }

    @Impure("transitively calls native function")
    static int impureRecursiveCallWithDependency2(int i) {
        return i % 2 == 0 ?
                impureRecursiveCallWithDependency1(-i) :
                impureRecursiveCallWithDependency3(i - 1);
    }

    @Impure("transitively calls native function")
    static int impureRecursiveCallWithDependency3(int i) {
        int j = pureCyclicRecursiveCall3(i);
        int k = impureRecursiveCallWithDependency1(j);
        int l = impureCallsSystemFunction(k);
        return pureCyclicRecursiveCall1(l);
    }

    @Impure("transitively calls native function")
    static int impureComplex1(int i) {
        int j = impureComplex2(i - 1);
        return impureComplex3(j * 2);
    }

    @Impure("transitively calls native function")
    static int impureComplex2(int i) {
        int j = impureComplex2(i - 1);
        return impureComplex1(j * 2);
    }

    @Impure("transitively calls native function")
    static int impureComplex3(int i) {
        int j = impureComplex4(i - 1);
        return impureComplex1(j * 2);
    }

    @Impure("transitively calls native function")
    static int impureComplex4(int i) {
        int j = impureComplex5(i - 1);
        return impureComplex2(j * 2);
    }

    @Impure("transitively calls native function")
    static int impureComplex5(int i) {
        int j = impureComplex6(i - 1);
        return impureComplex4(j * 2);
    }

    @Impure("transitively calls native function")
    static int impureComplex6(int i) {
        int j = impureComplex6(i - 1);
        return impureAtLast(impureComplex4(j * 2));
    }

    // --------------------------------------------------------------------------------------------
    // Two methods which are mutually dependent, but one depends on another pure method (where
    // the latter is also part of a mutual recursive dependency.
    //

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCallWithDependency1(int i) {
        return i < 0 ? i : pureRecursiveCallWithDependency2(i - 10);
    }

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCallWithDependency2(int i) {
        return i == 0 ?
                pureRecursiveCallWithDependency1(-i) :
                pureCallsMutuallyRecursivePureMethods(i - 1);
    }

    //---------------------------------------------------------------------------------------------
    // More tests for several levels of dependency
    //

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCall2DependencyLevels1(int i) {
        return i > 0 ?
                pureRecursiveCallWithDependency1(i * 5) :
                pureRecursiveCall2DependencyLevels2(10 - i);
    }

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCall2DependencyLevels2(int i) {
        return i <= 0 ? 0 : pureRecursiveCall2DependencyLevels1(i * i);
    }

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCall3DependencyLevels1(int i) {
        return i == 1 ? i : pureRecursiveCall3DependencyLevels2(-i);
    }

    @Pure("methods in all cycles are pure")
    static int pureRecursiveCall3DependencyLevels2(int i) {
        return i >= 0 ?
                pureRecursiveCall3DependencyLevels1(i + 100) :
                pureRecursiveCall2DependencyLevels2(100 - i);
    }

    // --------------------------------------------------------------------------------------------
    // All methods call directly or indirectly each other; but multiple cycles exist.
    //
    @Pure("methods in all cycles are pure")
    static int pureClosedSCC0(int i) {
        return i < 0 ? pureClosedSCC2(i - 10) : pureClosedSCC1(i - 111);
    }

    @Pure("methods in all cycles are pure")
    static int pureClosedSCC1(int i) {
        return i == 0 ? 32424 : pureClosedSCC3(i - 1);
    }

    @Pure("methods in all cycles are pure")
    static int pureClosedSCC2(int i) {
        return i > 0 ? 1001 : pureClosedSCC3(i - 3);
    }

    @Pure("methods in all cycles are pure")
    static int pureClosedSCC3(int i) {
        return pureClosedSCC0(i * 12121);
    }

    // --------------------------------------------------------------------------------------------
    // Impure, but takes "comparatively long to analyze"
    //
    @Impure("calls native function System.nanoTime()")
    public static int impureAtLast(int i) {
        int v = cpureCallsAbsCalleeCalleeCalleCallee(i);
        int u = impureRecursiveCallWithDependency1(impureRecursiveCallWithDependency2(v));
        int z = pureRecursiveCallWithDependency2(pureRecursiveCallWithDependency1(u));
        int l = pureClosedSCC2(pureClosedSCC1(pureClosedSCC0(z)));
        int j = impureComplex3(impureComplex2(impureComplex1(l)));
        int k = impureComplex6(impureComplex5(impureComplex4(j)));
        return (int) (k * System.getenv().size());
    }
}
