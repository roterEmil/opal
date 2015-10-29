/* BSD 2Clause License:
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
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED T
 * PURPOSE
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
package analysis

import org.opalj.br.Method
import org.opalj.br.instructions.INVOKESPECIAL
import org.opalj.br.analyses.SomeProject

/**
 * Common supertrait of all factory method properties.
 */
sealed trait FactoryMethod extends Property {
    final def key = FactoryMethod.Key // All instances have to share the SAME key!
}

/**
 * Common constants use by all [[FactoryMethod]] properties associated with methods.
 */
object FactoryMethod {

    /**
     * The key associated with every purity property.
     * It contains the unique name of the property and the default property that
     * will be used if no analysis is able to (directly) compute the respective property.
     */
    final val Key = PropertyKey.create("FactoryMethod", IsFactoryMethod)
}

/**
 * The respective method is a factory method.
 */
case object IsFactoryMethod extends FactoryMethod { final val isRefineable = false }

/**
 * The respective method is not a factory method.
 */
case object NotFactoryMethod extends FactoryMethod { final val isRefineable = false }

class FactoryMethodAnalysis private (
    project: SomeProject
)
        extends AbstractFPCFAnalysis[Method](
            project, FactoryMethodAnalysis.entitySelector
        ) {

    private[this] final val opInvokeSpecial = INVOKESPECIAL.opcode

    /**
     * Approximates if the given method is used as factory method. Any
     * native method is considered as factory method, because we have to
     * assume, that it creates an instance of the class.
     * This checks not for effective factory methods, since the return type
     * of the method is ignored. A method is either native or the constructor
     * of the declaring class is invoked.
     *
     * Possible improvements:
     *  - check if the methods returns an instance of the class or some superclass.
     */
    def determineProperty(
        method: Method
    ): PropertyComputationResult = {

        //TODO use escape analysis (still have to be implemented).

        if (method.isNative)
            return ImmediateResult(method, IsFactoryMethod)

        val classType = project.classFile(method).thisType

        val body = method.body.get
        val instructions = body.instructions
        val max = instructions.length
        var pc = 0
        while (pc < max) {
            val instruction = instructions(pc)
            if (instruction.opcode == opInvokeSpecial) {
                instruction match {
                    case INVOKESPECIAL(`classType`, "<init>", _) ⇒
                        return ImmediateResult(method, IsFactoryMethod)
                    case _ ⇒
                }
            }

            //TODO: model that the method could be called by an accessible method
            pc = body.pcOfNextInstruction(pc)
        }

        ImmediateResult(method, NotFactoryMethod)
    }
}

/**
 * Companion object for the [[FactoryMethodAnalysis]] class.
 */
object FactoryMethodAnalysis
        extends FPCFAnalysisRunner[FactoryMethodAnalysis] {

    private[FactoryMethodAnalysis] def entitySelector: PartialFunction[Entity, Method] = {
        case m: Method if m.isStatic && !m.isAbstract ⇒ m
    }

    private[FactoryMethodAnalysis] def apply(
        project: SomeProject
    ): FactoryMethodAnalysis = {
        new FactoryMethodAnalysis(project)
    }

    protected def start(project: SomeProject): Unit = {
        FactoryMethodAnalysis(project)
    }
}