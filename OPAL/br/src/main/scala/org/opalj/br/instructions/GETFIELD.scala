/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package br
package instructions

import org.opalj.collection.mutable.UShortSet

/**
 * Fetch ﬁeld from object.
 *
 * ==NOTE==
 * Getting an Array's length is translated to the special `arraylength` instruction.
 * E.g., in the following case:
 * {{{
 * Object[] os = ...; os.length
 * }}}
 * `os.length` is determined using the special `arraylength` instruction.
 *
 * @see [[org.opalj.br.instructions.FieldAccess]] for additional
 *      pattern matching support.
 *
 * @author Michael Eichberg
 */
case class GETFIELD(
        declaringClass: ObjectType,
        name:           String,
        fieldType:      FieldType
) extends FieldReadAccess {

    final def opcode: Opcode = GETFIELD.opcode

    final def mnemonic: String = "getfield"

    final def jvmExceptions: List[ObjectType] = FieldAccess.jvmExceptions

    final def numberOfPoppedOperands(ctg: Int ⇒ ComputationalTypeCategory): Int = 1

    final def stackSlotsChange: Int = -1 + fieldType.computationalType.operandSize

    final def nextInstructions(
        currentPC:             PC,
        regularSuccessorsOnly: Boolean
    )(
        implicit
        code: Code
    ): PCs = {
        if (regularSuccessorsOnly)
            UShortSet(indexOfNextInstruction(currentPC))
        else
            Instruction.nextInstructionOrExceptionHandler(
                this, currentPC, ObjectType.NullPointerException
            )
    }

    override def toString = "get "+declaringClass.toJava+"."+name+" : "+fieldType.toJava

}

/**
 * General information and factory methods.
 *
 * @author Malte Limmeroth
 */
object GETFIELD {

    final val opcode = 180

    /**
     * Factory method to create [[GETFIELD]] instructions.
     *
     * @param declaringClass the fields class name in JVM notation, e.g. "java/lang/Object"
     * @param name the fields name
     * @param fieldType the field type in JVM notation e.g. "Z" for boolean or `Ljava/lang/Object`
     *                  for general objects
     */
    def apply(
        declaringClass: String,
        name:           String,
        fieldType:      String
    ): GETFIELD = GETFIELD(ObjectType(declaringClass), name, FieldType(fieldType))

}
