/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses

import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.cg.TypeExtensibilityKey
import org.opalj.fpcf.properties.ClassImmutability
import org.opalj.fpcf.properties.ImmutableContainer
import org.opalj.fpcf.properties.ImmutableContainerType
import org.opalj.fpcf.properties.ImmutableObject
import org.opalj.fpcf.properties.ImmutableType
import org.opalj.fpcf.properties.MutableObject
import org.opalj.fpcf.properties.MutableType
import org.opalj.fpcf.properties.TypeImmutability

/**
 * Determines the mutability of a specific type by checking if all subtypes of a specific
 * type are immutable and checking that the set of types is closed.
 *
 * @author Michael Eichberg
 */
class TypeImmutabilityAnalysis( final val project: SomeProject) extends FPCFAnalysis {

    def doDetermineTypeMutability(
        typeExtensibility: ObjectType ⇒ Answer
    )(
        e: Entity
    ): PropertyComputationResult = e match {
        case t: ObjectType ⇒ step1(typeExtensibility)(t)
        case _ ⇒
            val m = e.getClass.getSimpleName+" is not an org.opalj.br.ObjectType"
            throw new IllegalArgumentException(m)
    }

    /**
     * @param t An object type which is not `java.lang.Object`.
     */
    def step1(
        typeExtensibility: ObjectType ⇒ Answer
    )(
        t: ObjectType
    ): PropertyComputationResult = {
        typeExtensibility(t) match {
            case Yes | Unknown ⇒ Result(t, MutableType)
            case No            ⇒ step2(t)
        }
    }

    def step2(t: ObjectType): PropertyComputationResult = {
        val directSubtypes = classHierarchy.directSubtypesOf(t)

        val cf = project.classFile(t)
        if (cf.exists(_.isFinal) || directSubtypes.isEmpty /*... the type is not extensible*/ ) {

            val c = new OnUpdateContinuation { c ⇒
                def apply(eps: SomeEPS): PropertyComputationResult = {
                    eps match {
                        case EPS(_, lb: ClassImmutability, ub: ClassImmutability) ⇒
                            val thisLB = lb.correspondingTypeImmutability
                            val thisUB = ub.correspondingTypeImmutability
                            if (eps.isFinal)
                                Result(t, thisUB)
                            else
                                IntermediateResult(
                                    t, thisLB, thisUB,
                                    Seq(eps), c, CheapPropertyComputation
                                )
                    }
                }
            }

            ps(t, ClassImmutability.key) match {
                case FinalEP(_, p) ⇒
                    Result(t, p.correspondingTypeImmutability)
                case eps @ IntermediateEP(_, lb, ub) ⇒
                    val thisUB = ub.correspondingTypeImmutability
                    val thisLB = lb.correspondingTypeImmutability
                    IntermediateResult(
                        t, thisLB, thisUB,
                        Seq(eps), c, CheapPropertyComputation
                    )
                case epk ⇒
                    IntermediateResult(
                        t, MutableType, ImmutableType,
                        Seq(epk), c, CheapPropertyComputation
                    )
            }
        } else {
            var dependencies = Map.empty[Entity, EOptionP[Entity, Property]]
            var joinedImmutability: TypeImmutability = ImmutableType // this may become "Mutable..."
            var maxImmutability: TypeImmutability = ImmutableType

            ps(t, ClassImmutability.key) match {
                case FinalEP(_, ImmutableObject) ⇒

                case FinalEP(_, _: MutableObject) ⇒
                    return Result(t, MutableType);

                case FinalEP(_, ImmutableContainer) ⇒
                    joinedImmutability = ImmutableContainerType
                    maxImmutability = ImmutableContainerType

                case eps @ IntermediateEP(_, lb, ub) ⇒
                    joinedImmutability = lb.correspondingTypeImmutability
                    maxImmutability = ub.correspondingTypeImmutability
                    dependencies += (t → eps)

                case eOptP ⇒
                    joinedImmutability = MutableType
                    dependencies += (t → eOptP)
            }

            directSubtypes foreach { subtype ⇒
                ps(subtype, TypeImmutability.key) match {
                    case FinalEP(_, ImmutableType) ⇒

                    case EPS(_, _, MutableType) ⇒
                        return Result(t, MutableType);

                    case FinalEP(_, ImmutableContainerType) ⇒
                        joinedImmutability = joinedImmutability.meet(ImmutableContainerType)
                        maxImmutability = ImmutableContainerType

                    case eps @ IntermediateEP(_, subtypeLB, subtypeUB) ⇒
                        joinedImmutability = joinedImmutability.meet(subtypeLB)
                        maxImmutability = maxImmutability.meet(subtypeUB)
                        dependencies += ((subtype, eps))

                    case epk ⇒
                        joinedImmutability = MutableType
                        dependencies += ((subtype, epk))

                }
            }

            if (dependencies.isEmpty) {
                Result(t, maxImmutability)
            } else if (joinedImmutability == maxImmutability) {
                // E.g., as soon as one subtype is an ImmutableContainer, we are at most
                // ImmutableContainer, even if all other subtype may even be immutable!
                Result(t, joinedImmutability)
            } else {
                // when we reach this point, we have dependencies to types for which
                // we have non-final information; joinedImmutability is either MutableType
                // or ImmutableContainer
                def c(eps: EPS[Entity, Property]): PropertyComputationResult = {

                    ///*debug*/ val previousDependencies = dependencies
                    ///*debug*/ val previousJoinedImmutability = joinedImmutability

                    def nextResult(): PropertyComputationResult = {
                        if (dependencies.isEmpty) {
                            Result(t, maxImmutability)
                        } else {
                            joinedImmutability = maxImmutability
                            val depIt = dependencies.valuesIterator
                            var continue = true
                            while (continue && depIt.hasNext) {
                                val n = depIt.next()
                                if (n.hasProperty)
                                    n.lb match {
                                        case lb: TypeImmutability ⇒
                                            joinedImmutability = joinedImmutability.meet(lb)
                                        case lb: ClassImmutability ⇒
                                            joinedImmutability = joinedImmutability.meet(lb.correspondingTypeImmutability)
                                    }
                                else {
                                    joinedImmutability = MutableType
                                    continue = false
                                }
                            }
                            if (joinedImmutability == maxImmutability) {
                                assert(maxImmutability == ImmutableContainerType)
                                Result(t, maxImmutability)
                            } else {
                                IntermediateResult(
                                    t, joinedImmutability, maxImmutability,
                                    dependencies.values, c
                                )
                            }
                        }
                    }

                    eps match {
                        case FinalEP(e, ImmutableType | ImmutableObject) ⇒
                            dependencies = dependencies - e
                            nextResult()

                        case EPS(_, _, MutableType | _: MutableObject) ⇒
                            Result(t, MutableType)

                        case FinalEP(e, ImmutableContainerType | ImmutableContainer) ⇒
                            maxImmutability = ImmutableContainerType
                            dependencies = dependencies - e
                            nextResult()

                        case eps @ IntermediateEP(e, _, subtypeP) ⇒
                            dependencies = dependencies.updated(
                                e, eps
                            )
                            subtypeP match {
                                case subtypeP: TypeImmutability ⇒
                                    maxImmutability = maxImmutability.meet(subtypeP)
                                case subtypeP: ClassImmutability ⇒
                                    maxImmutability = maxImmutability.meet(subtypeP.correspondingTypeImmutability)
                            }
                            nextResult()
                    }
                }

                IntermediateResult(t, joinedImmutability, maxImmutability, dependencies.values, c)
            }
        }
    }
}

trait TypeImmutabilityAnalysisScheduler extends ComputationSpecification {

    final override def derives: Set[PropertyKind] = Set(TypeImmutability)

    final override def uses: Set[PropertyKind] = Set(ClassImmutability)

    final override type InitializationData = Null
    final def init(p: SomeProject, ps: PropertyStore): Null = null

    def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    def afterPhaseCompletion(p: SomeProject, ps: PropertyStore): Unit = {}
}

/**
 * Starter for the '''type immutability analysis'''.
 *
 * @author Michael Eichberg
 */
object EagerTypeImmutabilityAnalysis
    extends TypeImmutabilityAnalysisScheduler
    with FPCFEagerAnalysisScheduler {

    override def start(project: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val typeExtensibility = project.get(TypeExtensibilityKey)
        val analysis = new TypeImmutabilityAnalysis(project)

        // An optimization, if the analysis also includes the JDK.
        ps.set(ObjectType.Object, MutableType)

        val types = project.allClassFiles.filter(_.thisType ne ObjectType.Object).map(_.thisType)

        ps.scheduleEagerComputationsForEntities(types) {
            analysis.step1(typeExtensibility)
        }

        analysis
    }

}

object LazyTypeImmutabilityAnalysis
    extends TypeImmutabilityAnalysisScheduler
    with FPCFLazyAnalysisScheduler {

    /**
     * Registers the analysis as a lazy computation, that is, the method
     * will call `ProperytStore.scheduleLazyComputation`.
     */
    override def startLazily(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {

        val typeExtensibility = p.get(TypeExtensibilityKey)
        val analysis = new TypeImmutabilityAnalysis(p)
        val analysisRunner: PropertyComputation[Entity] =
            analysis.doDetermineTypeMutability(typeExtensibility)

        // An optimization, if the analysis also includes the JDK.
        ps.set(ObjectType.Object, MutableType)
        ps.waitOnPhaseCompletion() // wait for ps.set to complete
        ps.registerLazyPropertyComputation(TypeImmutability.key, analysisRunner)
        analysis

    }
}
