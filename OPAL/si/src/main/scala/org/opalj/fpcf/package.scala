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
package org.opalj

import org.opalj.log.GlobalLogContext
import org.opalj.log.OPALLogger.info

/**
 * The fixpoint computations framework (`fpcf`) is a general framework to perform fixpoint
 * computations. The framework in particular supports the development of static analyses.
 *
 * In this case, the fixpoint computations/ static analyses are generally operating on the
 * code and need to be executed until the computation has reached its (implicit) fixpoint.
 * The fixpoint framework explicitly supports cyclic dependencies/computations.
 * A prime use case of the fixpoint framework are all those analyses that may interact with
 * the results of other analyses.
 *
 * For example, an analysis that analyzes all field write accesses to determine if we can
 * refine a field's type (for the purpose of the analysis) can (reuse) the information
 * about the return types of methods, which however may depend on the refined field types.
 *
 * The framework is generic enough to facilitate the implementation of
 * anytime algorithms.
 *
 * @note The dependency relation is as follows:
 *      “A depends on B”
 *          `===`
 *      “A is the depender, B is the dependee”.
 *          `===`
 *      “B is depended on by A”
 * @author Michael Eichberg
 */
package object fpcf {

    final val FrameworkName = "OPAL Static Analyses Infrastructure"

    {
        // Log the information whether a production build or a development build is used.
        implicit val logContext = GlobalLogContext
        try {
            assert(false)
            // when we reach this point assertions are turned off
            info(FrameworkName, "Production Build")
        } catch {
            case _: AssertionError ⇒ info(FrameworkName, "Development Build with Assertions")
        }
    }

    /**
     * The maximum number of Property Kinds that is (currently!) supported. Increasing this
     * number will increase the required memory.
     */
    final val SupportedPropertyKinds: Int = 128

    /**
     * The type of the values stored in a property store.
     *
     * Basically, a simple type alias to facilitate comprehension of the code.
     */
    final type Entity = AnyRef

    final type SomeEOptionP = EOptionP[_ <: Entity, _ <: Property]

    final type SomeEPK = EPK[_ <: Entity, _ <: Property]

    final type SomeEPS = EPS[_ <: Entity, _ <: Property]

    final type SomeFinalEP = FinalEP[_ <: Entity, _ <: Property]

    /**
     * A function that takes an entity and returns a result. The result maybe:
     *  - the final derived property,
     *  - a function that will continue computing the result once the information
     *    about some other entity is available or,
     *  - an intermediate result which may be refined later on, but not by the
     *    current running analysis.
     */
    final type PropertyComputation[E <: Entity] = (E) ⇒ PropertyComputationResult

    final type SomePropertyComputation = PropertyComputation[_ <: Entity]

    final type OnUpdateContinuation = (SomeEPS) ⇒ PropertyComputationResult

    /**
     * A function that continues the computation of a property. It takes
     * the entity + property of the entity on which the computation depends.
     */
    final type Continuation[P <: Property] = (Entity, P) ⇒ PropertyComputationResult

    final type SomeContinuation = Continuation[_ <: Property]

    final type SomePropertyKey = PropertyKey[_ <: Property]

    /**
     * The result of a computation if the computation derives multiple properties
     * at the same time.
     */
    final type ComputationResults = Traversable[SomeFinalEP]

    final type PropertyKeyID = Int

}
