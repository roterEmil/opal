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
package fpcf
package properties

import org.opalj.br.collection.{TypesSet ⇒ BRTypesSet}

/**
 * The set of exceptions that are potentially thrown by a specific method.
 * This includes the set of exceptions thrown by (transitively) called methods (if any).
 * The property '''does not take the exceptions of methods which override the respective
 * method into account'''.
 * Nevertheless, in case of a method call all potential receiver methods are
 * taken into consideration.
 *
 * Note that it may be possible to compute some meaningful upper type bound for the set of
 * thrown exceptions even if methods are called for which the set of thrown exceptions is unknown.
 * This is generally the case if those calls are all done in a try block but the catch/finally
 * blocks only calls known methods - if any.
 * An example is shown next and even if we assume that we don't know
 * the exceptions potentially thrown by `Class.forName` we could still determine that this method
 * will never throw an exception.
 * {{{
 * object Validator {
 *      def isAvailable(s : String) : Boolean = {
 *          try { Class.forName(s); true} finally {return false;}
 *      }
 * }
 * }}}
 *
 * Information about `ThrownExceptions` is generally associated with `DeclaredMethods`. I.e.,
 * the information is not attached to `Method` objects!
 *
 * Note that the top-value of the lattice is the empty set and the bottom value is the set
 * of all exceptions.
 *
 * @author Michael Eichberg
 */
case class ThrownExceptions(types: BRTypesSet) extends Property {

    final type Self = ThrownExceptions

    final def key = ThrownExceptions.Key

    /**
     * Returns `true` if and only if the method does not '''yet''' throw exceptions. I.e., if
     * this property is still refinable then this property may still change. Otherwise,
     * the analysis was able to determine that no exceptions are thrown.
     */
    def throwsNoExceptions: Boolean = types.isEmpty

    override def toString: String = {
        if (this == ThrownExceptions.MethodIsNative)
            """ThrownExceptionsAreUnknown(reason="method is native")"""
        else if (this == ThrownExceptions.UnknownExceptionIsThrown)
            """ThrownExceptionsAreUnknown(reason="the exception type is unknown")"""
        else if (this == ThrownExceptions.MethodBodyIsNotAvailable)
            """ThrownExceptionsAreUnknown(reason="method body is not available")"""
        else if (this == ThrownExceptions.AnalysisLimitation)
            """ThrownExceptionsAreUnknown(reason="analysis limitation")"""
        else if (this == ThrownExceptions.MethodCalledThrowsUnknownExceptions)
            """ThrownExceptionsAreUnknown(reason="Method called throws unknown exception")"""
        else if (this == ThrownExceptions.SomeException)
            """ThrownExceptionsAreUnknown(reason="<unspecified>")"""
        else
            s"ThrownExceptions(${types.toString})"
    }
}

object ThrownExceptions {

    def apply(types: BRTypesSet): ThrownExceptions = new ThrownExceptions(types)

    final val Key: PropertyKey[ThrownExceptions] = {
        PropertyKey.create[br.DeclaredMethod, ThrownExceptions](
            "ThrownExceptions",
            ThrownExceptionsFallback,
            (ps: PropertyStore, eps: EPS[br.DeclaredMethod, ThrownExceptions]) ⇒ eps.toUBEP
        )
    }

    final val NoExceptions: ThrownExceptions = new ThrownExceptions(BRTypesSet.empty)

    //
    // In the following we use specific instances to identify specific reasons...
    //

    final def SomeException = new ThrownExceptions(BRTypesSet.SomeException)

    final val MethodIsNative = new ThrownExceptions(BRTypesSet.SomeException)

    final val UnknownExceptionIsThrown = new ThrownExceptions(BRTypesSet.SomeException)

    final val MethodBodyIsNotAvailable = new ThrownExceptions(BRTypesSet.SomeException)

    final val AnalysisLimitation = new ThrownExceptions(BRTypesSet.SomeException)

    final val MethodIsAbstract = new ThrownExceptions(BRTypesSet.SomeException)

    final val MethodCalledThrowsUnknownExceptions = new ThrownExceptions(BRTypesSet.SomeException)

    final val UnresolvedInvokeDynamicInstruction = new ThrownExceptions(BRTypesSet.SomeException)

}
