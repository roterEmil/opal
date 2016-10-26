/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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

import org.opalj.bi.ACC_FINAL
import org.opalj.bi.ACC_PUBLIC
import org.opalj.bi.ACC_PROTECTED
import org.opalj.bi.ACC_PRIVATE
import org.opalj.bi.ACC_STATIC
import org.opalj.bi.VisibilityModifier
import org.opalj.bi.ACC_SYNTHETIC

/**
 * Abstractions over the common properties of class members (Methods and Fields).
 *
 * @author Michael Eichberg
 */
trait ClassMember extends ConcreteSourceElement {

    final def isPublic: Boolean = (ACC_PUBLIC.mask & accessFlags) != 0

    final def isProtected: Boolean = (ACC_PROTECTED.mask & accessFlags) != 0

    final def isPrivate: Boolean = (ACC_PRIVATE.mask & accessFlags) != 0

    final def hasDefaultVisibility: Boolean = (VisibilityModifier.mask & accessFlags) == 0

    final def isPackagePrivate = hasDefaultVisibility

    final def isStatic: Boolean = (ACC_STATIC.mask & accessFlags) != 0

    final def isNotStatic: Boolean = (ACC_STATIC.mask & accessFlags) == 0

    final def isFinal: Boolean = (ACC_FINAL.mask & accessFlags) != 0

    final def isNotFinal: Boolean = !isFinal

    /**
     * `True` if the `Synthetic` access flag or attribute is used.
     */
    final override def isSynthetic: Boolean = {
        super.isSynthetic || (ACC_SYNTHETIC.mask & accessFlags) != 0
    }

    /**
     * The simple name of this class member (method or field).
     */
    def name: String
}
/**
 * Defines an extractor method for class members.
 *
 * @author Michael Eichberg
 */
object ClassMember {

    def unapply(classMember: ClassMember): Option[Int] = Some(classMember.accessFlags)

}
