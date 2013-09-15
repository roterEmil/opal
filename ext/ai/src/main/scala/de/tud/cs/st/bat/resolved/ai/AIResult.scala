/* License (BSD Style License):
 * Copyright (c) 2009 - 2013
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
 *  - Neither the name of the Software Technology Group or Technische
 *    Universität Darmstadt nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific
 *    prior written permission.
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
package de.tud.cs.st
package bat
package resolved
package ai

/**
 * Factory to create `AIResult` objects.
 *
 * @author Michael Eichberg
 */
/* Design - We need to use a kind of builder to construct a Result object in two steps. 
 * This is necessary to correctly type the data structures that store the memory 
 * layout and which depend on the given domain. */
object AIResultBuilder {

    /**
     * Creates a domain dependent `AIAborted` object which stores the results of the
     * computation.
     */
    def aborted(
        theCode: Code,
        theDomain: Domain[_])(
            theWorkList: List[Int],
            theOperandsArray: Array[List[theDomain.DomainValue]],
            theLocalsArray: Array[Array[theDomain.DomainValue]]): AIAborted[theDomain.type] = {

        new AIAborted[theDomain.type] {
            val code: Code = theCode
            val domain: theDomain.type = theDomain
            val operandsArray: Array[List[theDomain.DomainValue]] = theOperandsArray
            val localsArray: Array[Array[theDomain.DomainValue]] = theLocalsArray
            val workList: List[Int] = theWorkList

            def continueInterpretation(): AIResult[domain.type] = {
                AI.continueInterpretation(code, domain)(workList, operandsArray, localsArray)
            }
        }
    }

    /**
     * Creates a domain dependent `AICompleted` object which stores the results of the
     * computation.
     */
    def completed(
        theCode: Code,
        theDomain: Domain[_])(
            theOperandsArray: Array[List[theDomain.DomainValue]],
            theLocalsArray: Array[Array[theDomain.DomainValue]]): AICompleted[theDomain.type] = {

        new AICompleted[theDomain.type] {
            val code: Code = theCode
            val domain: theDomain.type = theDomain
            val operandsArray: Array[List[theDomain.DomainValue]] = theOperandsArray
            val localsArray: Array[Array[theDomain.DomainValue]] = theLocalsArray

            def restartInterpretation(): AIResult[theDomain.type] = {
                AI.continueInterpretation(code, domain)(List(0), operandsArray, localsArray)
            }
        }
    }
}

/**
 * Encapsulates the result of the abstract interpretation of a method.
 */
/* Design - We use an explicit type parameter to avoid a path dependency on a 
 * concrete AIResult instance. I.e., if we would remove the type parameter 
 * we would introduce a path dependence to a particular AIResult's instance and the actual 
 * type would be "this.domain.type" and "this.domain.DomainValue". */
sealed abstract class AIResult[D <: Domain[_]] {
    val code: Code
    val domain: D
    val operandsArray: Array[List[D#DomainValue]]
    val localsArray: Array[Array[D#DomainValue]]
    val workList: List[Int]

    /**
     * Returns `true` if the abstract interpretation was aborted.
     */
    def wasAborted: Boolean

}

sealed abstract class AIAborted[D <: Domain[_]] extends AIResult[D] {

    def wasAborted: Boolean = true

    def continueInterpretation(): AIResult[domain.type]
}

sealed abstract class AICompleted[D <: Domain[_]] extends AIResult[D] {

    val workList: List[Int] = List.empty

    def wasAborted: Boolean = false

    def restartInterpretation(): AIResult[domain.type]
}
