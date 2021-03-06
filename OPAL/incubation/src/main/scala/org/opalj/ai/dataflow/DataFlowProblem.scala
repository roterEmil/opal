/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package ai
package dataflow

import scala.collection.{Map, Set}
import br._
import br.analyses._
import org.opalj.value.ValueInformation

/**
 * Characterizes a data-flow problem. The characterization consists of the specification
 * of the problem as well as the selection of the solver.
 *
 * I.e., tries to find paths from the identified sources to the identified sinks.
 *
 * ==Overall Initialization==
 * Overall initialization is done in multiple steps.
 *
 *  1. The parameters are checked.
 *  1. The parameters of the analysis are set.
 *  1. The project is initialized by the framework and also set.
 *  1. Initialize [[sourceValues]] and [[sinkInstructions]] (These methods needs to be
 *     overridden by your subclass.)
 *  1. Call [[solve]]. After you have called [[solve]] you are no longer allowed
 *      to change the project or the sources and sinks.
 *
 * @author Michael Eichberg and Ben Hermann
 */
trait DataFlowProblem[Source, P] {

    val p: P // type of the parameters/parameter

    val project: Project[Source]

    // __________________________________________________________________________________
    //
    // Functionality required to specify the taint-information flow
    //
    //

    type DomainValue <: AnyRef

    /**
     * Encapsultates taint information about a(n implicit) value.
     */
    protected[this] trait TaintInformation {
        def isTainted(): Boolean
    }

    /**
     * The (implicitly referred to) value is not tainted.
     */
    case object NotTainted extends TaintInformation {
        final override def isTainted(): Boolean = false
    }

    /**
     * Factory method that – given a `DomainValue` – creates a [[TaintInformation]]
     * object that encapsulates the information that the value is not tainted.
     */
    val ValueIsNotTainted: (DomainValue) ⇒ TaintInformation = (DomainValue) ⇒ NotTainted

    /**
     * Representation of a tainted value.
     */
    protected[this] trait TaintedValue extends TaintInformation {
        final override def isTainted(): Boolean = true

        def valueInformation: ValueInformation
        def domainValue: DomainValue
    }

    /**
     * Returns a factory method that – given a `DomainValue` – creates a [[TaintedValue]]
     * object that encapsulates the information that the value is tainted.
     */
    def ValueIsTainted: (DomainValue) ⇒ TaintInformation

    /**
     * Extractor to match tainted values.
     */
    object Tainted {
        def unapply(value: TaintedValue): Some[ValueInformation] = Some(value.valueInformation)
    }

    case class Invoke(
            declaringClassType: ReferenceType,
            name:               String,
            descriptor:         MethodDescriptor,
            context:            Method,
            caller:             TaintInformation,
            receiver:           TaintInformation,
            parameters:         IndexedSeq[TaintInformation]
    )

    case class CallResult(
            receiver:   TaintInformation,
            parameters: IndexedSeq[TaintInformation],
            result:     (DomainValue) ⇒ TaintInformation
    )

    type OnCallTaintProcessor = PartialFunction[Invoke, CallResult]

    protected[this] var onCallTaintProcessors: List[OnCallTaintProcessor] = List.empty

    def call(f: OnCallTaintProcessor): Unit = {
        onCallTaintProcessors = f :: onCallTaintProcessors
    }

    case class FieldWrite(
            declaringClassType: ReferenceType,
            name:               String,
            fieldType:          Type,
            context:            Method,
            caller:             TaintInformation,
            value:              TaintInformation,
            receiver:           TaintInformation
    )

    type OnWriteTaintProcessor = PartialFunction[FieldWrite, (DomainValue) ⇒ TaintInformation /*about the receiver*/ ]

    protected[this] var onWriteTaintProcessors: List[OnWriteTaintProcessor] = List.empty

    def write(f: OnWriteTaintProcessor): Unit = {
        onWriteTaintProcessors = f :: onWriteTaintProcessors
    }

    // __________________________________________________________________________________
    //
    // Identifies the analysis' context
    //
    //

    /**
     * Identifies the values that we want to track (by means of the origin of the
     * respective value) per relevant method.
     *
     * ''The returned map must not change, after solve was called!''
     *
     * @note The methods have to belong to the [[project]].
     *
     * @see [[org.opalj.ai.dataflow.spec.DataFlowProblemSpecification]] for the easy creation
     *      of the `sourcesValues` map.
     */
    def sourceValues(): Map[Method, Set[ValueOrigin]]

    /**
     * Identifies the program counters (PCs) of those instructions
     * that are sinks.
     *
     * ''The returned map must not change, after solve was called!''
     *
     * @note The methods have to belong to the [[project]].
     *
     * @see [[org.opalj.ai.dataflow.spec.DataFlowProblemSpecification]] for the easy creation
     *      of the `sinkInstructions` map.
     */
    def sinkInstructions(): Map[Method, Set[PC]]

    protected[this] def analyzeFeasability(): Unit = {
        val sourceValuesCount = sourceValues.values.view.map(pcs ⇒ pcs.size).sum
        if (project.methodsCount / 10 < sourceValuesCount) {
            Console.out.println(
                "[info] The analysis will take long; the number of source values to analyze is: "+
                    sourceValuesCount+
                    "."
            )
        }
    }

    def initializeSourcesAndSinks(): Unit

    /**
     * Tries to find paths from the sources to the sinks.
     */
    def solve(): String = {

        analyzeFeasability()

        doSolve()
    }

    /*abstract*/ def doSolve(): String
}
