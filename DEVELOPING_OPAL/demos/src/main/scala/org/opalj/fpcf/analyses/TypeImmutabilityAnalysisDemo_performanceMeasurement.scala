/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.analyses

import java.net.URL

import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.ProjectAnalysisApplication
import org.opalj.br.fpcf.FPCFAnalysesManagerKey
import org.opalj.br.fpcf.analyses.LazyL0FieldImmutabilityAnalysis
import org.opalj.br.fpcf.analyses.LazyStaticDataUsageAnalysis
import org.opalj.br.fpcf.analyses.LazyUnsoundPrematurelyReadFieldsAnalysis
import org.opalj.fpcf.PropertyStore
import org.opalj.tac.cg.RTACallGraphKey
import org.opalj.tac.fpcf.analyses.EagerLxTypeImmutabilityAnalysis_new
import org.opalj.tac.fpcf.analyses.LazyFieldLocalityAnalysis
import org.opalj.tac.fpcf.analyses.LazyL0ReferenceImmutabilityAnalysis
import org.opalj.tac.fpcf.analyses.LazyLxClassImmutabilityAnalysis_new
import org.opalj.tac.fpcf.analyses.escape.LazyReturnValueFreshnessAnalysis
import org.opalj.tac.fpcf.analyses.escape.LazySimpleEscapeAnalysis
import org.opalj.tac.fpcf.analyses.purity.LazyL2PurityAnalysis
import org.opalj.util.PerformanceEvaluation.time
import org.opalj.util.Seconds

/**
 * Runs the EagerLxClassImmutabilityAnalysis_new as well as analysis needed for improving the result
 *
 * @author Tobias Peter Roth
 */
object TypeImmutabilityAnalysisDemo_performanceMeasurement extends ProjectAnalysisApplication {

    override def title: String = "run EagerLxTypeImmutabilityAnalysis_new"

    override def description: String = "run EagerLxTypeImmutabilityAnalysis_new"

    override def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {
        val result = analyze(project)
        BasicReport(result)
    }

    def analyze(theProject: Project[URL]): String = {

        var times: List[Seconds] = Nil: List[Seconds]
        for (i ← 0 until 10) {
            val project = Project.recreate(theProject)
            val analysesManager = project.get(FPCFAnalysesManagerKey)
            analysesManager.project.get(RTACallGraphKey)
            var propertyStore: PropertyStore = null
            var analysisTime: Seconds = Seconds.None
            time {
                propertyStore = analysesManager
                    .runAll(
                        LazyUnsoundPrematurelyReadFieldsAnalysis,
                        LazyL2PurityAnalysis,
                        LazyL0ReferenceImmutabilityAnalysis,
                        LazyL0FieldImmutabilityAnalysis,
                        EagerLxTypeImmutabilityAnalysis_new,
                        LazyLxClassImmutabilityAnalysis_new,
                        LazyFieldLocalityAnalysis,
                        LazySimpleEscapeAnalysis,
                        LazyReturnValueFreshnessAnalysis,
                        LazyStaticDataUsageAnalysis
                    )
                    ._1
                propertyStore.waitOnPhaseCompletion();
            } { t ⇒
                analysisTime = t.toSeconds
            }
            times = analysisTime :: times
        }

        /**
         * "Mutable Type: "+propertyStore
         * .finalEntities(MutableType_new)
         * .toList
         * .toString()+"\n"+
         * "Shallow Immutable Type: "+propertyStore
         * .finalEntities(ShallowImmutableType)
         * .toList
         * .toString()+"\n"+
         * "Dependent Immutable Type: "+propertyStore
         * .finalEntities(DependentImmutableType)
         * .toList
         * .toString()+"\n"+
         * "Deep Immutable Type: "+propertyStore
         * .finalEntities(DeepImmutableType)
         * .toList
         * .toString()+"\n"*
         */
        times.foreach(s ⇒ println(s+" seconds"))
        val aver = times.fold(new Seconds(0))((x: Seconds, y: Seconds) ⇒ x + y).timeSpan / times.size
        f"took: $aver seconds on average"
    }
}
