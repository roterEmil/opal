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
 */
trait TypeAnnotationPath {

    def attribute_length: Int

    def toXHTML(implicit cp: Constant_Pool): Node
}

case object TypeAnnotationDirectlyOnType extends TypeAnnotationPath {

    final override def attribute_length: Int = 1

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <div class="type_annotation_path"><b>Path</b> DirectlyOnType</div>
    }
}

trait TypeAnnotationPathElement {

    def type_path_kind: Int

    def type_argument_index: Int

    def toXHTML(implicit cp: Constant_Pool): Node
}

case class TypeAnnotationPathElements(
        path: IndexedSeq[TypeAnnotationPathElement]
) extends TypeAnnotationPath {

    final override def attribute_length: Int = 1 + path.length * 2

    def toXHTML(implicit cp: Constant_Pool): Node = {
        val path = <ol>{ this.path.map(pe ⇒ <li>{ pe.toXHTML(cp) }</li>) }</ol>

        // return node (this comment is a necessary technical artifact...)
        <div class="type_annotation_path"><b>Path</b>{ path }</div>
    }
}

/**
 * The `type_path_kind` was `0` (and the type_argument_index was also `0`).
 */
case object TypeAnnotationDeeperInArrayType extends TypeAnnotationPathElement {

    final override def type_path_kind: Int = 0

    final override def type_argument_index: Int = 0

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <div class="type_annotation_path_element">DeeperInArrayType</div>
    }
}

case object TypeAnnotationDeeperInNestedType extends TypeAnnotationPathElement {

    final override def type_path_kind: Int = 1

    final override def type_argument_index: Int = 0

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <div class="type_annotation_path_element">DeeperInNestedType</div>
    }
}

case object TypeAnnotationOnBoundOfWildcardType extends TypeAnnotationPathElement {

    final override def type_path_kind: Int = 2

    final override def type_argument_index: Int = 0

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <div class="type_annotation_path_element">OnBoundOfWildcardType</div>
    }
}

case class TypeAnnotationOnTypeArgument(type_argument_index: Int) extends TypeAnnotationPathElement {

    final override def type_path_kind: Int = 3

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <div class="type_annotation_path_element">OnTypeArgument: { type_argument_index }</div>
    }
}
