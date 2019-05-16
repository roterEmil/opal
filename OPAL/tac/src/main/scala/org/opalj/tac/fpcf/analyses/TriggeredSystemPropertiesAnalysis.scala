/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses

import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPK
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.InterimEUBP
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.NoResult
import org.opalj.fpcf.PartialResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyKey
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.SomeEPS
import org.opalj.fpcf.UBP
import org.opalj.value.ValueInformation
import org.opalj.br.fpcf.properties.SystemProperties
import org.opalj.br.DeclaredMethod
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.cg.properties.Callers
import org.opalj.br.fpcf.cg.properties.NoCallers
import org.opalj.br.fpcf.BasicFPCFTriggeredAnalysisScheduler
import org.opalj.tac.fpcf.properties.TACAI
import org.opalj.tac.fpcf.properties.TheTACAI

class TriggeredSystemPropertiesAnalysis private[analyses] (
        final val project: SomeProject
) extends FPCFAnalysis {

    def analyze(declaredMethod: DeclaredMethod): PropertyComputationResult = {
        // todo this is copy & past code from the RTACallGraphAnalysis -> refactor
        (propertyStore(declaredMethod, Callers.key): @unchecked) match {
            case FinalP(NoCallers) ⇒
                // nothing to do, since there is no caller
                return NoResult;

            case eps: EPS[_, _] ⇒
                if (eps.ub eq NoCallers) {
                    // we can not create a dependency here, so the analysis is not allowed to create
                    // such a result
                    throw new IllegalStateException("illegal immediate result for callers")
                }
            // the method is reachable, so we analyze it!
        }

        // we only allow defined methods
        if (!declaredMethod.hasSingleDefinedMethod)
            return NoResult;

        val method = declaredMethod.definedMethod

        // we only allow defined methods with declared type eq. to the class of the method
        if (method.classFile.thisType != declaredMethod.declaringClassType)
            return NoResult;

        if (method.body.isEmpty)
            // happens in particular for native methods
            return NoResult;

        val tacaiEP = propertyStore(method, TACAI.key)
        if (tacaiEP.hasUBP && tacaiEP.ub.tac.isDefined) {
            processMethod(declaredMethod, tacaiEP.asEPS)
        } else {
            InterimPartialResult(
                None,
                Some(tacaiEP),
                continuation(declaredMethod)
            )
        }

    }

    def continuation(declaredMethod: DeclaredMethod)(eps: SomeEPS): PropertyComputationResult = {
        eps match {
            case UBP(TheTACAI(_)) ⇒
                processMethod(declaredMethod, eps.asInstanceOf[EPS[Method, TACAI]])
            case _ ⇒
                InterimPartialResult(
                    None,
                    Some(eps),
                    continuation(declaredMethod)
                )
        }
    }

    def processMethod(
        declaredMethod: DeclaredMethod, tacaiEP: EPS[Method, TACAI]
    ): PropertyComputationResult = {
        assert(tacaiEP.hasUBP && tacaiEP.ub.tac.isDefined)
        val stmts = tacaiEP.ub.tac.get.stmts

        var propertyMap: Map[String, Set[String]] = Map.empty

        for (stmt ← stmts) stmt match {
            case VirtualFunctionCallStatement(call) if (call.name == "setProperty" || call.name == "put") && classHierarchy.isSubtypeOf(call.declaringClass, ObjectType("java/util/Properties")) ⇒
                propertyMap = computeProperties(propertyMap, call.params, stmts)
            case StaticMethodCall(_, ObjectType.System, _, "setProperty", _, params) ⇒
                propertyMap = computeProperties(propertyMap, params, stmts)
            case _ ⇒
        }

        if (propertyMap.isEmpty) {
            return NoResult;
        }

        def update(
            currentVal: EOptionP[SomeProject, SystemProperties]
        ): Option[EPS[SomeProject, SystemProperties]] = currentVal match {
            case UBP(ub) ⇒
                var oldProperties = ub.properties
                val noNewProperty = propertyMap.forall {
                    case (key, values) ⇒
                        oldProperties.contains(key) && {
                            val oldValues = oldProperties(key)
                            values.forall(oldValues.contains)
                        }
                }

                if (noNewProperty) {
                    None
                } else {
                    for ((key, values) ← propertyMap) {
                        val oldValues = oldProperties.getOrElse(key, Set.empty)
                        oldProperties = oldProperties.updated(key, oldValues ++ values)
                    }
                    Some(InterimEUBP(project, new SystemProperties(propertyMap)))
                }

            case _: EPK[SomeProject, SystemProperties] ⇒
                Some(InterimEUBP(project, new SystemProperties(propertyMap)))
        }

        if (tacaiEP.isFinal) {
            PartialResult[SomeProject, SystemProperties](
                project,
                SystemProperties.key,
                update
            )
        } else {
            InterimPartialResult(
                project,
                SystemProperties.key,
                update,
                Some(tacaiEP),
                continuation(declaredMethod)
            )
        }
    }

    def computeProperties(
        propertyMap: Map[String, Set[String]],
        params:      Seq[Expr[DUVar[ValueInformation]]],
        stmts:       Array[Stmt[DUVar[ValueInformation]]]
    ): Map[String, Set[String]] = {
        var res = propertyMap

        assert(params.size == 2)
        val possibleKeys = getPossibleStrings(params.head, stmts)
        val possibleValues = getPossibleStrings(params(1), stmts)

        for (key ← possibleKeys) {
            val values = res.getOrElse(key, Set.empty)
            res = res.updated(key, values ++ possibleValues)
        }

        res
    }

    def getPossibleStrings(
        value: Expr[DUVar[ValueInformation]], stmts: Array[Stmt[DUVar[ValueInformation]]]
    ): Set[String] = {
        value.asVar.definedBy filter { index ⇒
            index >= 0 && stmts(index).asAssignment.expr.isStringConst
        } map { stmts(_).asAssignment.expr.asStringConst.value }
    }

}

object TriggeredSystemPropertiesAnalysis extends BasicFPCFTriggeredAnalysisScheduler {

    override def uses: Set[PropertyBounds] = Set(
        PropertyBounds.ub(Callers),
        PropertyBounds.ub(TACAI)
    )

    override def triggeredBy: PropertyKey[Callers] = Callers.key

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): TriggeredSystemPropertiesAnalysis = {
        val analysis = new TriggeredSystemPropertiesAnalysis(p)
        ps.registerTriggeredComputation(triggeredBy, analysis.analyze)
        analysis
    }

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def derivesCollaboratively: Set[PropertyBounds] = Set(
        PropertyBounds.ub(SystemProperties)
    )
}
