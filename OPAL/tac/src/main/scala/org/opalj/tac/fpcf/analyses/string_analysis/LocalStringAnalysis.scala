/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.InterimLUBP
import org.opalj.fpcf.InterimResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Result
import org.opalj.fpcf.SomeEPS
import org.opalj.br.analyses.SomeProject
import org.opalj.br.cfg.CFG
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.FPCFAnalysisScheduler
import org.opalj.br.fpcf.FPCFLazyAnalysisScheduler
import org.opalj.br.fpcf.cg.properties.Callees
import org.opalj.br.fpcf.properties.StringConstancyProperty
import org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation
import org.opalj.tac.ExprStmt
import org.opalj.tac.SimpleTACAIKey
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.fpcf.analyses.purity.LazyL2PurityAnalysis.derivedProperty
import org.opalj.tac.fpcf.analyses.string_analysis.interpretation.InterpretationHandler
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.AbstractPathFinder
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.FlatPathElement
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.NestedPathElement
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.Path
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.PathTransformer
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.SubPath
import org.opalj.tac.fpcf.analyses.string_analysis.preprocessing.WindowPathFinder
import org.opalj.tac.fpcf.properties.TACAI

/**
 * LocalStringAnalysis processes a read operation of a local string variable at a program
 * position, ''pp'', in a way that it finds the set of possible strings that can be read at ''pp''.
 *
 * "Local" as this analysis takes into account only the enclosing function as a context, i.e., it
 * intraprocedural. Values coming from other functions are regarded as dynamic values even if the
 * function returns a constant string value.
 *
 * The StringConstancyProperty might contain more than one possible string, e.g., if the source of
 * the value is an array.
 *
 * @author Patrick Mell
 */
class LocalStringAnalysis(
        val project: SomeProject
) extends FPCFAnalysis {

    /**
     * This class is to be used to store state information that are required at a later point in
     * time during the analysis, e.g., due to the fact that another analysis had to be triggered to
     * have all required information ready for a final result.
     */
    private case class ComputationState(
            // The lean path that was computed
            computedLeanPath: Path,
            // A mapping from DUVar elements to the corresponding indices of the FlatPathElements
            var2IndexMapping: mutable.Map[V, Int],
            // A mapping from values of FlatPathElements to StringConstancyInformation
            fpe2sci: mutable.Map[Int, StringConstancyInformation],
            // The control flow graph on which the computedLeanPath is based
            cfg: CFG[Stmt[V], TACStmts[V]]
    )

    def analyze(data: P): ProperPropertyComputationResult = {
        // sci stores the final StringConstancyInformation (if it can be determined now at all)
        var sci = StringConstancyProperty.lowerBound.stringConstancyInformation
        val tacProvider = p.get(SimpleTACAIKey)
        val cfg = tacProvider(data._2).cfg
        val stmts = cfg.code.instructions

        val uvar = data._1
        val defSites = uvar.definedBy.toArray.sorted
        // Function parameters are currently regarded as dynamic value; the following if finds read
        // operations of strings (not String{Builder, Buffer}s, they will be handles further down
        if (defSites.head < 0) {
            return Result(data, StringConstancyProperty.lowerBound)
        }
        val pathFinder: AbstractPathFinder = new WindowPathFinder(cfg)

        // If not empty, this very routine can only produce an intermediate result
        val dependees = mutable.Map[Entity, EOptionP[Entity, Property]]()
        // state will be set to a non-null value if this analysis needs to call other analyses /
        // itself; only in the case it calls itself, will state be used, thus, it is valid to
        // initialize it with null
        var state: ComputationState = null

        val call = stmts(defSites.head).asAssignment.expr
        if (InterpretationHandler.isStringBuilderBufferToStringCall(call)) {
            val initDefSites = InterpretationHandler.findDefSiteOfInit(uvar, stmts)
            // initDefSites empty => String{Builder,Buffer} from method parameter is to be evaluated
            if (initDefSites.isEmpty) {
                return Result(data, StringConstancyProperty.lowerBound)
            }

            val paths = pathFinder.findPaths(initDefSites, uvar.definedBy.head)
            val leanPaths = paths.makeLeanPath(uvar, stmts)

            // Find DUVars, that the analysis of the current entity depends on
            val dependentVars = findDependentVars(leanPaths, stmts, uvar)
            if (dependentVars.nonEmpty) {
                dependentVars.keys.foreach { nextVar ⇒
                    val toAnalyze = (nextVar, data._2)
                    val fpe2sci = mutable.Map[Int, StringConstancyInformation]()
                    state = ComputationState(leanPaths, dependentVars, fpe2sci, cfg)
                    val ep = propertyStore(toAnalyze, StringConstancyProperty.key)
                    ep match {
                        case FinalP(p) ⇒
                            return processFinalP(data, dependees.values, state, ep.e, p)
                        case _ ⇒
                            dependees.put(toAnalyze, ep)
                    }
                }
            } else {
                sci = new PathTransformer(cfg).pathToStringTree(leanPaths).reduce(true)
            }
        } // If not a call to String{Builder, Buffer}.toString, then we deal with pure strings
        else {
            val interHandler = InterpretationHandler(cfg)
            sci = StringConstancyInformation.reduceMultiple(
                uvar.definedBy.toArray.sorted.flatMap { interHandler.processDefSite }.toList
            )
        }

        if (dependees.nonEmpty) {
            InterimResult(
                data,
                StringConstancyProperty.upperBound,
                StringConstancyProperty.lowerBound,
                dependees.values,
                continuation(data, dependees.values, state)
            )
        } else {
            Result(data, StringConstancyProperty(sci))
        }
    }

    /**
     * `processFinalP` is responsible for handling the case that the `propertyStore` outputs a
     * [[FinalP]].
     */
    private def processFinalP(
        data:      P,
        dependees: Iterable[EOptionP[Entity, Property]],
        state:     ComputationState,
        e:         Entity,
        p:         Property
    ): ProperPropertyComputationResult = {
        // Add mapping information (which will be used for computing the final result)
        val retrievedProperty = p.asInstanceOf[StringConstancyProperty]
        val currentSci = retrievedProperty.stringConstancyInformation
        state.fpe2sci.put(state.var2IndexMapping(e.asInstanceOf[P]._1), currentSci)

        // No more dependees => Return the result for this analysis run
        val remDependees = dependees.filter(_.e != e)
        if (remDependees.isEmpty) {
            val finalSci = new PathTransformer(state.cfg).pathToStringTree(
                state.computedLeanPath, state.fpe2sci.toMap
            ).reduce(true)
            Result(data, StringConstancyProperty(finalSci))
        } else {
            InterimResult(
                data,
                StringConstancyProperty.upperBound,
                StringConstancyProperty.lowerBound,
                remDependees,
                continuation(data, remDependees, state)
            )
        }
    }

    /**
     * Continuation function.
     *
     * @param data The data that was passed to the `analyze` function.
     * @param dependees A list of dependencies that this analysis run depends on.
     * @param state The computation state (which was originally captured by `analyze` and possibly
     *              extended / updated by other methods involved in computing the final result.
     * @return This function can either produce a final result or another intermediate result.
     */
    private def continuation(
        data:      P,
        dependees: Iterable[EOptionP[Entity, Property]],
        state:     ComputationState
    )(eps: SomeEPS): ProperPropertyComputationResult = eps match {
        case FinalP(p) ⇒ processFinalP(data, dependees, state, eps.e, p)
        case InterimLUBP(lb, ub) ⇒ InterimResult(
            data, lb, ub, dependees, continuation(data, dependees, state)
        )
        case _ ⇒ throw new IllegalStateException("Could not process the continuation successfully.")
    }

    /**
     * Helper / accumulator function for finding dependees. For how dependees are detected, see
     * [[findDependentVars]]. Returns a list of pairs of DUVar and the index of the
     * [[FlatPathElement.element]] in which it occurs.
     */
    private def findDependeesAcc(
        subpath:           SubPath,
        stmts:             Array[Stmt[V]],
        target:            V,
        foundDependees:    ListBuffer[(V, Int)],
        hasTargetBeenSeen: Boolean
    ): (ListBuffer[(V, Int)], Boolean) = {
        var encounteredTarget = false
        subpath match {
            case fpe: FlatPathElement ⇒
                if (target.definedBy.contains(fpe.element)) {
                    encounteredTarget = true
                }
                // For FlatPathElements, search for DUVars on which the toString method is called
                // and where these toString calls are the parameter of an append call
                stmts(fpe.element) match {
                    case ExprStmt(_, outerExpr) ⇒
                        if (InterpretationHandler.isStringBuilderBufferAppendCall(outerExpr)) {
                            val param = outerExpr.asVirtualFunctionCall.params.head.asVar
                            param.definedBy.filter(_ >= 0).foreach { ds ⇒
                                val expr = stmts(ds).asAssignment.expr
                                if (InterpretationHandler.isStringBuilderBufferToStringCall(expr)) {
                                    foundDependees.append((
                                        outerExpr.asVirtualFunctionCall.params.head.asVar,
                                        fpe.element
                                    ))
                                }
                            }
                        }
                    case _ ⇒
                }
                (foundDependees, encounteredTarget)
            case npe: NestedPathElement ⇒
                npe.element.foreach { nextSubpath ⇒
                    if (!encounteredTarget) {
                        val (_, seen) = findDependeesAcc(
                            nextSubpath, stmts, target, foundDependees, encounteredTarget
                        )
                        encounteredTarget = seen
                    }
                }
                (foundDependees, encounteredTarget)
            case _ ⇒ (foundDependees, encounteredTarget)
        }
    }

    /**
     * Takes a `path`, this should be the lean path of a [[Path]], as well as a context in the form
     * of statements, `stmts`, and detects all dependees within `path`. Dependees are found by
     * looking at all elements in the path, and check whether the argument of an `append` call is a
     * value that stems from a `toString` call of a [[StringBuilder]] or [[StringBuffer]]. This
     * function then returns the found UVars along with the indices of those append statements.
     *
     * @note In order to make sure that a [[org.opalj.tac.DUVar]] does not depend on itself, pass
     *       this variable as `ignore`.
     */
    private def findDependentVars(
        path: Path, stmts: Array[Stmt[V]], ignore: V
    ): mutable.LinkedHashMap[V, Int] = {
        val dependees = mutable.LinkedHashMap[V, Int]()
        val ignoreNews = InterpretationHandler.findNewOfVar(ignore, stmts)
        var wasTargetSeen = false

        path.elements.foreach { nextSubpath ⇒
            if (!wasTargetSeen) {
                val (currentDeps, encounteredTarget) = findDependeesAcc(
                    nextSubpath, stmts, ignore, ListBuffer(), hasTargetBeenSeen = false
                )
                wasTargetSeen = encounteredTarget
                currentDeps.foreach { nextPair ⇒
                    val newExpressions = InterpretationHandler.findNewOfVar(nextPair._1, stmts)
                    if (ignore != nextPair._1 && ignoreNews != newExpressions) {
                        dependees.put(nextPair._1, nextPair._2)
                    }
                }
            }
        }
        dependees
    }

}

sealed trait LocalStringAnalysisScheduler extends FPCFAnalysisScheduler {

    final override def uses: Set[PropertyBounds] = Set(
        PropertyBounds.ub(TACAI),
        PropertyBounds.ub(Callees),
        PropertyBounds.lub(StringConstancyProperty)
    )

    final override type InitializationData = LocalStringAnalysis
    final override def init(p: SomeProject, ps: PropertyStore): InitializationData = {
        new LocalStringAnalysis(p)
    }

    override def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    override def afterPhaseScheduling(ps: PropertyStore, analysis: FPCFAnalysis): Unit = {}

    override def afterPhaseCompletion(
        p:        SomeProject,
        ps:       PropertyStore,
        analysis: FPCFAnalysis
    ): Unit = {}

}

/**
 * Executor for the lazy analysis.
 */
object LazyLocalStringAnalysis extends LocalStringAnalysisScheduler with FPCFLazyAnalysisScheduler {

    override def register(
        p: SomeProject, ps: PropertyStore, analysis: InitializationData
    ): FPCFAnalysis = {
        val analysis = new LocalStringAnalysis(p)
        ps.registerLazyPropertyComputation(StringConstancyProperty.key, analysis.analyze)
        analysis
    }

    override def derivesLazily: Some[PropertyBounds] = Some(derivedProperty)

}
