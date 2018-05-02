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
package purity

import org.opalj.br.AnnotationLike
import org.opalj.br.BooleanValue
import org.opalj.br.MethodDescriptor
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject

/**
 * Base trait for matchers that match a method's `Purity` property.
 *
 * @author Dominik Helm
 */
sealed abstract class PurityMatcher(val property: Purity) extends AbstractPropertyMatcher {

    override def isRelevant(
        p:  SomeProject,
        as: Set[ObjectType],
        e:  Entity,
        a:  AnnotationLike
    ): Boolean = {
        val annotationType = a.annotationType.asObjectType

        val analysesElementValues =
            getValue(p, annotationType, a.elementValuePairs, "analyses").asArrayValue.values
        val analyses = analysesElementValues.map(ev ⇒ ev.asClassValue.value.asObjectType)

        val eps = getValue(p, annotationType, a.elementValuePairs, "eps").asArrayValue.values.map(ev ⇒ ev.asAnnotationValue.annotation)
        val negate = getValue(p, annotationType, a.elementValuePairs, "negate").asInstanceOf[BooleanValue].value

        analyses.exists(as.contains) && (eps.forall(negate ^ evaluateEP(p, as, _, negate)))
    }

    def evaluateEP(
        project: SomeProject,
        as:      Set[ObjectType],
        ep:      AnnotationLike,
        negate:  Boolean
    ): Boolean = {
        val annotationType = ep.annotationType.asObjectType

        val classType = getValue(project, annotationType, ep.elementValuePairs, "cf").asClassValue.value.asObjectType

        val field = getValue(project, annotationType, ep.elementValuePairs, "field").asStringValue.value
        val method = getValue(project, annotationType, ep.elementValuePairs, "method").asStringValue.value

        val analysesElementValues =
            getValue(project, annotationType, ep.elementValuePairs, "analyses").asArrayValue.values
        val analyses = analysesElementValues.map(ev ⇒ ev.asClassValue.value.asObjectType)

        if (!analyses.isEmpty && !analyses.exists(as.contains)) {
            return !negate // Analysis specific ep requirement, but analysis was not executed
        }

        val pk = getValue(project, annotationType, ep.elementValuePairs, "pk").asStringValue.value match {
            case "Purity"               ⇒ Purity.key
            case "FieldMutability"      ⇒ FieldMutability.key
            case "ClassImmutability"    ⇒ ClassImmutability.key
            case "ReturnValueFreshness" ⇒ ReturnValueFreshness.key
            case "FieldLocality"        ⇒ FieldLocality.key
        }
        val p = getValue(project, annotationType, ep.elementValuePairs, "p").asStringValue.value

        val propertyStore = project.get(PropertyStoreKey)

        def checkProperty(eop: EOptionP[Entity, Property]): Boolean = {
            if (eop.hasProperty)
                eop.ub.toString == p
            else
                PropertyKey.fallbackProperty(propertyStore, eop.e, eop.pk).toString == p
        }

        if (field != "") {
            val cfo = project.classFile(classType)
            cfo exists { cf ⇒
                cf findField field exists { field ⇒
                    checkProperty(propertyStore(field, pk))
                }
            }
        } else if (method != "") {
            val declaredMethods = project.get(DeclaredMethodsKey)
            val descriptorIndex = method.indexOf('(')
            val methodName = method.substring(0, descriptorIndex)
            val descriptor = MethodDescriptor(method.substring(descriptorIndex))
            val cfo = project.classFile(classType)

            cfo exists { cf ⇒
                cf findMethod (methodName, descriptor) exists { method ⇒
                    checkProperty(propertyStore(declaredMethods(method), pk))
                }
            }
        } else {
            checkProperty(propertyStore(classType, pk))
        }
    }

    def validateProperty(
        p:          SomeProject,
        as:         Set[ObjectType],
        entity:     Entity,
        a:          AnnotationLike,
        properties: Traversable[Property]
    ): Option[String] = {
        if (!properties.exists(_ match {
            case `property` ⇒ true
            case _          ⇒ false
        })) {
            // ... when we reach this point the expected property was not found.
            Some(a.elementValuePairs.head.value.asStringValue.value)
        } else {
            None
        }
    }
}

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.CompileTimePure]].
 */
class CompileTimePureMatcher extends PurityMatcher(org.opalj.fpcf.properties.CompileTimePure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBPure]].
 */
class PureMatcher extends PurityMatcher(org.opalj.fpcf.properties.LBPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBSideEffectFree]].
 */
class SideEffectFreeMatcher extends PurityMatcher(org.opalj.fpcf.properties.LBSideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBExternallyPure]].
 */
class ExternallyPureMatcher extends PurityMatcher(org.opalj.fpcf.properties.LBExternallyPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBExternallySideEffectFree]].
 */
class ExternallySideEffectFreeMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBExternallySideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBContextuallyPure]].
 */
class ContextuallyPureMatcher extends PurityMatcher(org.opalj.fpcf.properties.LBContextuallyPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBContextuallySideEffectFree]].
 */
class ContextuallySideEffectFreeMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBContextuallySideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDPure]].
 */
class DomainSpecificPureMatcher extends PurityMatcher(org.opalj.fpcf.properties.LBDPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDSideEffectFree]].
 */
class DomainSpecificSideEffectFreeMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBDSideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDExternallyPure]].
 */
class DomainSpecificExternallyPureMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBDExternallyPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDExternallySideEffectFree]].
 */
class DomainSpecificExternallySideEffectFreeMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBDExternallySideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDContextuallyPure]].
 */
class DomainSpecificContextuallyPureMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBDContextuallyPure)

/**
 * Matches a method's `Purity` property. The match is successful if the method has the property
 * [[org.opalj.fpcf.properties.LBDContextuallySideEffectFree]].
 */
class DomainSpecificContextuallySideEffectFreeMatcher
    extends PurityMatcher(org.opalj.fpcf.properties.LBDContextuallySideEffectFree)

/**
 * Matches a method's `Purity` property. The match is successful if the property is an instance of
 * [[org.opalj.fpcf.properties.ClassifiedImpure]].
 */
class ImpureMatcher extends PurityMatcher(org.opalj.fpcf.properties.Impure) {

    override def validateProperty(
        p:          SomeProject,
        as:         Set[ObjectType],
        entity:     Entity,
        a:          AnnotationLike,
        properties: Traversable[Property]
    ): Option[String] = {
        if (!properties.exists(_ match {
            case _: ClassifiedImpure ⇒ true
            case _                   ⇒ false
        })) {
            // ... when we reach this point the expected property was not found.
            Some(a.elementValuePairs.head.value.asStringValue.value)
        } else {
            None
        }
    }

}