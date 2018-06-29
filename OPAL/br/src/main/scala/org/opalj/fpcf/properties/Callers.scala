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

import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.MethodIDs
import org.opalj.br.analyses.SomeProject

import scala.collection.Set

/**
 * For a given [[DeclaredMethod]], and for each call site (represented by the PC), the set of methods
 * that are possible call targets.
 *
 * @author Florian Kuebler
 */
sealed trait CallersPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = Callers
}

sealed trait Callers extends Property with OrderedProperty with CallersPropertyMetaInformation {

    def size: Int

    def callers: Set[(DeclaredMethod, Int /*PC*/ )] //TODO: maybe use traversable instead of set

    def updated(caller: DeclaredMethod, pc: Int): Callers

    override def toString: String = {
        s"Callers(size=${this.size})"
    }

    final def key: PropertyKey[Callers] = Callers.key

    /**
     * Tests if this property is equal or better than the given one (better means that the
     * value is above the given value in the underlying lattice.)
     */
    override def checkIsEqualOrBetterThan(e: Entity, other: Callers): Unit = {
        if (other.size < size) //todo if (!pcMethodPairs.subsetOf(other.pcMethodPairs))
            throw new IllegalArgumentException(s"$e: illegal refinement of property $other to $this")
    }
}

final class CallersImplementation(
        private[this] val encodedCallers:  Set[Long /*MethodId + PC*/ ],
        private[this] val methodIds:       MethodIDs,
        private[this] val declaredMethods: DeclaredMethods
) extends Callers {

    override def callers: Set[(DeclaredMethod, Int /*PC*/ )] = {
        for {
            encodedPair ← encodedCallers
            (mId, pc) = Callers.toMethodAndPc(encodedPair)
        } yield declaredMethods(methodIds(mId)) → pc
    }

    override val size: Int = {
        encodedCallers.size
    }

    override def updated(declaredCaller: DeclaredMethod, pc: Int): Callers = {
        assert(declaredCaller.hasDefinition)
        val definedCaller = declaredCaller.asDefinedMethod
        val caller = definedCaller.methodDefinition
        assert(definedCaller.declaringClassType eq caller.classFile.thisType)

        new CallersImplementation(
            encodedCallers + Callers.toLong(methodIds(caller), pc),
            methodIds, declaredMethods
        )
    }
}

class FallbackCallers(project: SomeProject, method: DeclaredMethod) extends Callers {

    override lazy val size: Int = {
        //callees.size * project.allMethods.size
        // todo this is for performance improvement only
        Int.MaxValue
    }

    override def callers: Set[(DeclaredMethod, Int /*PC*/ )] = {
        ??? // todo
    }

    override def updated(caller: DeclaredMethod, pc: Int): Callers = this
}

object Callers extends CallersPropertyMetaInformation {

    final val key: PropertyKey[Callers] = {
        PropertyKey.create(
            "Callers",
            (ps: PropertyStore, m: DeclaredMethod) ⇒ Callers.fallback(m, ps.context[SomeProject]),
            (_: PropertyStore, eps: EPS[DeclaredMethod, Callers]) ⇒ eps.ub,
            (_: PropertyStore, _: DeclaredMethod) ⇒ None
        )
    }

    def fallback(m: DeclaredMethod, p: SomeProject): Callers = {
        new FallbackCallers(p, m)
    }

    def toLong(methodId: Int, pc: Int): Long = {
        (methodId.toLong << 32) | (pc & 0xFFFFFFFFL)
    }
    def toMethodAndPc(methodAndPc: Long): (Int, Int) = {
        ((methodAndPc >> 32).toInt, methodAndPc.toInt)
    }
}
