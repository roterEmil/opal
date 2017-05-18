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
package ba

/**
 * Builder for a [[org.opalj.br.Field]].
 *
 * @author Malte Limmeroth
 * @author Michael Eichberg
 */
case class FIELD(
        val accessModifiers:    AccessModifier,
        val name:               String,
        val descriptor:         String,
        val attributesBuilders: Seq[br.FieldAttributeBuilder] = Seq.empty
) {

    /**
     * Returns the build [[org.opalj.br.Method]] and its annotations.
     */
    def buildBRField(): br.Field = {
        val fieldType = br.FieldType(descriptor)
        val accessFlags = accessModifiers.accessFlags
        val attributes = attributesBuilders map { attributeBuilder ⇒
            attributeBuilder(accessFlags, name, fieldType)
        }

        br.Field(accessFlags, name, fieldType, attributes)
    }

}

case class FIELDS(fields: FIELD*) {

    /**
     * Returns the build [[org.opalj.br.Method]] and its code annotations.
     */
    def buildBRFields(): Seq[br.Field] = fields.map(f ⇒ f.buildBRField())

}