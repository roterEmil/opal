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

import org.opalj.br.ObjectType

/**
 * Determines if an object potentially leaks its self reference (`this`) by passing
 * assigning it to fields or passing it to methods which assign it to fields.
 * Hence, it computes a special escape information.
 *
 * Here, the self-reference escapes the scope of a class if:
 *  - ... it is assigned to a (static) field,
 *  - ... it is passed to a method that assigns it to a field,
 *  - ... it is stored in an array,
 *  - ... it is returned (recall that constructors have a void return type),
 *  - ... if a superclass leaks the self reference.
 *
 * This property can be used as a foundation for an analysis that determines whether
 * all instances created for a specific class never escape their creating methods and,
 * hence, respective types cannot occur outside the respective contexts.
 */
sealed trait SelfReferenceLeakage extends Property {

    final type Self = SelfReferenceLeakage

    final def key = SelfReferenceLeakage.Key
}

/**
 * Models the top of the self-references leakage lattice.
 */
case object DoesNotLeakSelfReference extends SelfReferenceLeakage

/**
 * Models the bottom of the lattice.
 */
case object LeaksSelfReference extends SelfReferenceLeakage

object SelfReferenceLeakage {

    final val Key = PropertyKey.create[ObjectType, SelfReferenceLeakage](
        name = "org.opalj.SelfReferenceLeakage",
        fallbackProperty = LeaksSelfReference
    )

}
