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
package da

import scala.xml.Node

/**
 * @author Michael Eichberg
 * @author Wael Alkhatib
 * @author Isbel Isbel
 * @author Noorulla Sharief
 * @author Andre Pacak
 */
trait VerificationTypeInfo {

    /**
     * The number of bytes required to store the VerificationTypeInfo
     * information in a class file.
     */
    def attribute_length: Int

    def tag: Int

    def toXHTML(implicit cp: Constant_Pool): Node

}

object VerificationTypeInfo {
    final val ITEM_Top = 0
    final val ITEM_Integer = 1
    final val ITEM_Float = 2
    final val ITEM_Long = 4
    final val ITEM_Double = 3
    final val ITEM_Null = 5
    final val ITEM_UninitializedThis = 6
    final val ITEM_Object = 7
    final val ITEM_Unitialized = 8
}

case object TopVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Top

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">top |</span>

}

case object IntegerVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Integer

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">int |</span>

}

case object FloatVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Float

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">float |</span>

}

case object LongVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Long

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">long |</span>

}

case object DoubleVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Double

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">double |</span>

}

case object NullVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_Null

    def toXHTML(implicit cp: Constant_Pool): Node = <span class="verification">null |</span>

}

case object UninitializedThisVariableInfo extends VerificationTypeInfo {

    final override def attribute_length: Int = 1

    def tag: Int = VerificationTypeInfo.ITEM_UninitializedThis

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="verification">uninitializedThis |</span>
    }
}

case class ObjectVariableInfo(cpool_index: Int) extends VerificationTypeInfo {

    final override def attribute_length: Int = 1 + 2

    def tag: Int = VerificationTypeInfo.ITEM_Object

    def toXHTML(implicit cp: Constant_Pool): Node = {
        val referenceType = asJavaReferenceType(cpool_index)
        referenceType.asSpan("")
    }
}

case class UninitializedVariableInfo(val offset: Int) extends VerificationTypeInfo {

    final override def attribute_length: Int = 1 + 2

    def tag: Int = VerificationTypeInfo.ITEM_Unitialized

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="verification">Uninitialized(pc: { offset }) |</span>
    }
}

