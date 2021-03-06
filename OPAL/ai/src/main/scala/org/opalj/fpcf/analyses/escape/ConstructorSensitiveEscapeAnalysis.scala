/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses
package escape

import org.opalj.br.DefinedMethod
import org.opalj.br.ObjectType
import org.opalj.br.analyses.VirtualFormalParameter
import org.opalj.fpcf.properties.AtMost
import org.opalj.fpcf.properties.EscapeInCallee
import org.opalj.fpcf.properties.EscapeProperty
import org.opalj.fpcf.properties.EscapeViaAbnormalReturn
import org.opalj.fpcf.properties.EscapeViaHeapObject
import org.opalj.fpcf.properties.EscapeViaParameter
import org.opalj.fpcf.properties.EscapeViaParameterAndAbnormalReturn
import org.opalj.fpcf.properties.EscapeViaStaticField
import org.opalj.fpcf.properties.GlobalEscape
import org.opalj.fpcf.properties.NoEscape
import org.opalj.tac.NonVirtualMethodCall

/**
 * Special handling for constructor calls, as the receiver of an constructor is always an
 * allocation site.
 * The constructor of Object does not escape the self reference by definition. For other
 * constructors, the inter-procedural chain will be processed until it reaches the Object
 * constructor or escapes. Is this the case, leastRestrictiveProperty will be set to the lower bound
 * of the current value and the calculated escape state.
 *
 * For non constructor calls, [[org.opalj.fpcf.properties.AtMost]]
 * ([[org.opalj.fpcf.properties.EscapeInCallee]]) of `e will be ` returned whenever the receiver or
 * a parameter is a use of defSite.
 *
 * @author Florian Kuebler
 */
trait ConstructorSensitiveEscapeAnalysis extends AbstractEscapeAnalysis {

    override type AnalysisContext <: AbstractEscapeAnalysisContext with PropertyStoreContainer with VirtualFormalParametersContainer with DeclaredMethodsContainer

    abstract protected[this] override def handleThisLocalOfConstructor(
        call: NonVirtualMethodCall[V]
    )(
        implicit
        context: AnalysisContext,
        state:   AnalysisState
    ): Unit = {
        assert(call.name == "<init>", "method is not a constructor")
        assert(context.usesDefSite(call.receiver), "call receiver does not use def-site")

        // the object constructor will not escape the this local
        if (call.declaringClass eq ObjectType.Object)
            return ;

        // resolve the constructor
        val constructor = project.specialCall(
            context.targetMethodDeclaringClassType,
            call.declaringClass, call.isInterface, name = "<init>", call.descriptor
        )
        constructor match {
            case Success(callee) ⇒
                // check if the this local escapes in the callee

                val fp = context.virtualFormalParameters(context.declaredMethods(callee))(0)
                if (fp != context.entity) {
                    val escapeState = context.propertyStore(fp, EscapeProperty.key)
                    handleEscapeState(escapeState)
                }
            case /* unknown method */ _ ⇒ state.meetMostRestrictive(AtMost(NoEscape))
        }
    }

    private[this] def handleEscapeState(
        eOptionP: EOptionP[Entity, Property]
    )(implicit state: AnalysisState): Unit = {
        eOptionP match {
            case FinalEP(_, NoEscape) ⇒ //NOTHING TO DO

            case FinalEP(_, GlobalEscape) ⇒
                state.meetMostRestrictive(GlobalEscape)

            case FinalEP(_, EscapeViaStaticField) ⇒
                state.meetMostRestrictive(EscapeViaStaticField)

            case FinalEP(_, EscapeViaHeapObject) ⇒
                state.meetMostRestrictive(EscapeViaHeapObject)

            case FinalEP(_, EscapeInCallee) ⇒
                state.meetMostRestrictive(EscapeInCallee)

            case FinalEP(_, AtMost(EscapeInCallee)) ⇒
                state.meetMostRestrictive(AtMost(EscapeInCallee))

            case FinalEP(_, EscapeViaParameter) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, EscapeViaAbnormalReturn) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, EscapeViaParameterAndAbnormalReturn) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, AtMost(NoEscape)) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, AtMost(EscapeViaParameter)) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, AtMost(EscapeViaAbnormalReturn)) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, AtMost(EscapeViaParameterAndAbnormalReturn)) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))

            case FinalEP(_, p) ⇒
                throw new UnknownError(s"unexpected escape property ($p) for constructors")

            case ep @ IntermediateEP(_, _, NoEscape) ⇒
                state.addDependency(ep)

            case ep @ IntermediateEP(_, _, EscapeInCallee) ⇒
                state.meetMostRestrictive(EscapeInCallee)
                state.addDependency(ep)

            case ep @ IntermediateEP(_, _, AtMost(EscapeInCallee)) ⇒
                state.meetMostRestrictive(AtMost(EscapeInCallee))
                state.addDependency(ep)

            case ep @ IntermediateEP(_, _, _) ⇒
                state.meetMostRestrictive(AtMost(NoEscape))
                state.addDependency(ep)

            // result not yet finished
            case epk ⇒
                state.addDependency(epk)
        }
    }

    abstract override protected[this] def continuation(
        someEPS: SomeEPS
    )(implicit context: AnalysisContext, state: AnalysisState): PropertyComputationResult = {

        someEPS.e match {
            case VirtualFormalParameter(DefinedMethod(_, method), -1) if method.isConstructor ⇒
                state.removeDependency(someEPS)
                handleEscapeState(someEPS)
                returnResult
            case _ ⇒ super.continuation(someEPS)
        }
    }
}
