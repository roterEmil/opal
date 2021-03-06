/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses
package escape

import org.opalj.ai.ValueOrigin
import org.opalj.ai.common.DefinitionSitesKey
import org.opalj.br.DefinedMethod
import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.VirtualFormalParameter
import org.opalj.br.analyses.VirtualFormalParameters
import org.opalj.br.analyses.VirtualFormalParametersKey
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.cfg.CFG
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.properties.AtMost
import org.opalj.fpcf.properties.EscapeProperty
import org.opalj.fpcf.properties.NoEscape
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.TACode

class SimpleEscapeAnalysisContext(
        val entity:                  Entity,
        val defSite:                 ValueOrigin,
        val targetMethod:            Method,
        val uses:                    IntTrieSet,
        val code:                    Array[Stmt[V]],
        val cfg:                     CFG[Stmt[V], TACStmts[V]],
        val declaredMethods:         DeclaredMethods,
        val virtualFormalParameters: VirtualFormalParameters,
        val project:                 SomeProject,
        val propertyStore:           PropertyStore
) extends AbstractEscapeAnalysisContext
    with PropertyStoreContainer
    with VirtualFormalParametersContainer
    with DeclaredMethodsContainer
    with CFGContainer

/**
 * A simple escape analysis that can handle [[org.opalj.ai.common.DefinitionSiteLike]]s and
 * [[org.opalj.br.analyses.VirtualFormalParameter]]s (the this parameter of a constructor). All other
 * [[org.opalj.br.analyses.VirtualFormalParameter]]s are marked as
 * [[org.opalj.fpcf.properties.AtMost]]([[org.opalj.fpcf.properties.NoEscape]]).
 *
 *
 *
 *
 * @author Florian Kuebler
 */
class SimpleEscapeAnalysis( final val project: SomeProject)
    extends DefaultEscapeAnalysis
    with ConstructorSensitiveEscapeAnalysis
    with ConfigurationBasedConstructorEscapeAnalysis
    with SimpleFieldAwareEscapeAnalysis
    with ExceptionAwareEscapeAnalysis {

    override type AnalysisContext = SimpleEscapeAnalysisContext
    override type AnalysisState = AbstractEscapeAnalysisState

    override def determineEscapeOfFP(fp: VirtualFormalParameter): PropertyComputationResult = {
        fp match {
            case VirtualFormalParameter(DefinedMethod(_, m), _) if m.body.isEmpty ⇒
                Result(fp, AtMost(NoEscape))
            case VirtualFormalParameter(DefinedMethod(_, m), -1) if m.isInitializer ⇒
                val TACode(params, code, _, cfg, _, _) = tacaiProvider(m)
                val useSites = params.thisParameter.useSites
                val ctx = createContext(fp, -1, m, useSites, code, cfg)
                doDetermineEscape(ctx, createState)
            case VirtualFormalParameter(_, _) ⇒
                //TODO IntermediateResult(fp, GlobalEscape, AtMost(NoEscape), Seq.empty, (_) ⇒ throw new RuntimeException())
                Result(fp, AtMost(NoEscape))
        }
    }

    override def createContext(
        entity:       Entity,
        defSite:      ValueOrigin,
        targetMethod: Method,
        uses:         IntTrieSet,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]]
    ): SimpleEscapeAnalysisContext = new SimpleEscapeAnalysisContext(
        entity,
        defSite,
        targetMethod,
        uses,
        code,
        cfg,
        declaredMethods,
        virtualFormalParameters,
        project,
        propertyStore
    )

    override def createState: AbstractEscapeAnalysisState = new AbstractEscapeAnalysisState {}
}

trait SimpleEscapeAnalysisScheduler extends ComputationSpecification {

    final override def derives: Set[PropertyKind] = Set(EscapeProperty)

    final override def uses: Set[PropertyKind] = Set.empty

    final override type InitializationData = Null
    final def init(p: SomeProject, ps: PropertyStore): Null = null

    def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    def afterPhaseCompletion(p: SomeProject, ps: PropertyStore): Unit = {}

}

/**
 * A companion object used to start the analysis.
 */
object EagerSimpleEscapeAnalysis
    extends SimpleEscapeAnalysisScheduler
    with FPCFEagerAnalysisScheduler {

    override def start(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val fps = p.get(VirtualFormalParametersKey).virtualFormalParameters
        val ass = p.get(DefinitionSitesKey).getAllocationSites
        val analysis = new SimpleEscapeAnalysis(p)
        ps.scheduleEagerComputationsForEntities(fps ++ ass)(analysis.determineEscape)
        analysis
    }
}

object LazySimpleEscapeAnalysis
    extends SimpleEscapeAnalysisScheduler
    with FPCFLazyAnalysisScheduler {

    /**
     * Registers the analysis as a lazy computation, that is, the method
     * will call `ProperytStore.scheduleLazyComputation`.
     */
    override def startLazily(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val analysis = new SimpleEscapeAnalysis(p)
        ps.registerLazyPropertyComputation(EscapeProperty.key, analysis.determineEscape)
        analysis
    }
}
