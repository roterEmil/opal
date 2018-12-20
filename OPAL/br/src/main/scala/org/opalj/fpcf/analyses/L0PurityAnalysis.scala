/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses

import scala.annotation.switch
import org.opalj.br.ArrayType
import org.opalj.br.DefinedMethod
import org.opalj.br.ObjectType
import org.opalj.br.VirtualDeclaredMethod
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.instructions._
import org.opalj.fpcf.properties.FieldMutability
import org.opalj.fpcf.properties.FinalField
import org.opalj.fpcf.properties.ImmutableContainerType
import org.opalj.fpcf.properties.ImmutableType
import org.opalj.fpcf.properties.ImpureByLackOfInformation
import org.opalj.fpcf.properties.ImpureByAnalysis
import org.opalj.fpcf.properties.NonFinalField
import org.opalj.fpcf.properties.Purity
import org.opalj.fpcf.properties.TypeImmutability
import org.opalj.fpcf.properties.Pure
import org.opalj.fpcf.properties.CompileTimePure

/**
 * Very simple, fast, sound but also imprecise analysis of the purity of methods. See the
 * [[org.opalj.fpcf.properties.Purity]] property for details regarding the precise
 * semantics of `(Im)Pure`.
 *
 * This analysis is a very, very shallow implementation that immediately gives
 * up, when something "complicated" (e.g., method calls which take objects)
 * is encountered. It also does not perform any significant control-/data-flow analyses.
 *
 * @author Michael Eichberg
 */
class L0PurityAnalysis private[analyses] ( final val project: SomeProject) extends FPCFAnalysis {

    import project.nonVirtualCall
    import project.resolveFieldReference

    private[this] val declaredMethods: DeclaredMethods = project.get(DeclaredMethodsKey)

    /** Called when the analysis is scheduled lazily. */
    def doDeterminePurity(e: Entity): ProperPropertyComputationResult = {
        e match {
            case m: DefinedMethod         ⇒ determinePurity(m)
            case m: VirtualDeclaredMethod ⇒ Result(m, ImpureByLackOfInformation)
            case _                        ⇒ throw new IllegalArgumentException(s"$e is not a method")
        }
    }

    /**
     * Determines the purity of the method starting with the instruction with the given
     * pc. If the given pc is larger than 0 then all previous instructions (in particular
     * method calls) must not violate this method's purity.
     *
     * This function encapsulates the continuation.
     */
    def doDeterminePurityOfBody(
        definedMethod:    DefinedMethod,
        initialDependees: Set[EOptionP[Entity, Property]]
    ): ProperPropertyComputationResult = {

        val method = definedMethod.definedMethod
        val declaringClassType = method.classFile.thisType
        val methodDescriptor = method.descriptor
        val methodName = method.name
        val body = method.body.get
        val instructions = body.instructions
        val maxPC = instructions.length

        var dependees = initialDependees

        var currentPC = 0
        while (currentPC < maxPC) {
            val instruction = instructions(currentPC)
            (instruction.opcode: @switch) match {
                case GETSTATIC.opcode ⇒
                    val GETSTATIC(declaringClass, fieldName, fieldType) = instruction

                    resolveFieldReference(declaringClass, fieldName, fieldType) match {

                        // ... we have no support for arrays at the moment
                        case Some(field) if !field.fieldType.isArrayType ⇒
                            // The field has to be effectively final and -
                            // if it is an object – immutable!
                            val fieldType = field.fieldType
                            if (fieldType.isArrayType) {
                                return Result(declaringClass, ImpureByAnalysis);
                            }
                            if (!fieldType.isBaseType) {
                                propertyStore(fieldType, TypeImmutability.key) match {
                                    case FinalP(ImmutableType) ⇒
                                    case FinalP(_) ⇒
                                        return Result(definedMethod, ImpureByAnalysis);
                                    case ep ⇒
                                        dependees += ep
                                }
                            }
                            if (field.isNotFinal) {
                                propertyStore(field, FieldMutability.key) match {
                                    case FinalP(_: FinalField) ⇒
                                    case FinalP(_) ⇒
                                        return Result(definedMethod, ImpureByAnalysis);
                                    case ep ⇒
                                        dependees += ep
                                }
                            }

                        case _ ⇒
                            // We know nothing about the target field (it is not
                            // found in the scope of the current project).
                            return Result(definedMethod, ImpureByAnalysis);
                    }

                case INVOKESPECIAL.opcode | INVOKESTATIC.opcode ⇒ instruction match {

                    case MethodInvocationInstruction(`declaringClassType`, _, `methodName`, `methodDescriptor`) ⇒
                    // We have a self-recursive call; such calls do not influence
                    // the computation of the method's purity and are ignored.
                    // Let's continue with the evaluation of the next instruction.

                    case mii: NonVirtualMethodInvocationInstruction ⇒

                        nonVirtualCall(declaringClassType, mii) match {

                            case Success(callee) ⇒
                                /* Recall that self-recursive calls are handled earlier! */
                                val purity = propertyStore(declaredMethods(callee), Purity.key)

                                purity match {
                                    case FinalP(CompileTimePure | Pure) ⇒ /* Nothing to do */

                                    // Handling cyclic computations
                                    case ep @ InterimUBP(Pure)          ⇒ dependees += ep

                                    case _: EPS[_, _] ⇒
                                        return Result(definedMethod, ImpureByAnalysis);

                                    case epk ⇒
                                        dependees += epk
                                }

                            case _ /* Empty or Failure */ ⇒
                                // We know nothing about the target method (it is not
                                // found in the scope of the current project).
                                return Result(definedMethod, ImpureByAnalysis);

                        }
                }

                case GETFIELD.opcode |
                    PUTFIELD.opcode | PUTSTATIC.opcode |
                    AALOAD.opcode | AASTORE.opcode |
                    BALOAD.opcode | BASTORE.opcode |
                    CALOAD.opcode | CASTORE.opcode |
                    SALOAD.opcode | SASTORE.opcode |
                    IALOAD.opcode | IASTORE.opcode |
                    LALOAD.opcode | LASTORE.opcode |
                    DALOAD.opcode | DASTORE.opcode |
                    FALOAD.opcode | FASTORE.opcode |
                    ARRAYLENGTH.opcode |
                    MONITORENTER.opcode | MONITOREXIT.opcode |
                    INVOKEDYNAMIC.opcode | INVOKEVIRTUAL.opcode | INVOKEINTERFACE.opcode ⇒
                    return Result(definedMethod, ImpureByAnalysis);

                case ARETURN.opcode |
                    IRETURN.opcode | FRETURN.opcode | DRETURN.opcode | LRETURN.opcode |
                    RETURN.opcode ⇒
                // if we have a monitor instruction the method is impure anyway..
                // hence, we can ignore the monitor related implicit exception

                // Reference comparisons may have different results for structurally equal values
                case IF_ACMPEQ.opcode | IF_ACMPNE.opcode ⇒
                    return Result(definedMethod, ImpureByAnalysis);

                case _ ⇒
                    // All other instructions (IFs, Load/Stores, Arith., etc.) are pure
                    // as long as no implicit exceptions are raised.
                    // Remember that NEW/NEWARRAY/etc. may raise OutOfMemoryExceptions.
                    if (instruction.jvmExceptions.nonEmpty) {
                        // JVM Exceptions reify the stack and, hence, make the method impure as
                        // the calling context is now an explicit part of the method's result.
                        return Result(definedMethod, ImpureByAnalysis);
                    }
                // else ok..

            }
            currentPC = body.pcOfNextInstruction(currentPC)
        }

        // IN GENERAL
        // Every method that is not identified as being impure is (conditionally)pure.
        if (dependees.isEmpty)
            return Result(definedMethod, Pure);

        // This function computes the “purity for a method based on the properties of its dependees:
        // other methods (Purity), types (immutability), fields (effectively final)
        def c(eps: SomeEPS): ProperPropertyComputationResult = {
            // Let's filter the entity.
            dependees = dependees.filter(_.e ne eps.e)

            eps match {
                // We can't report any real result as long as we don't know that the fields are all
                // effectively final and the types are immutable.

                case FinalP(_: FinalField | ImmutableType) ⇒
                    if (dependees.isEmpty) {
                        Result(definedMethod, Pure)
                    } else {
                        // We still have dependencies regarding field mutability/type immutability;
                        // hence, we have nothing to report.
                        InterimResult(definedMethod, ImpureByAnalysis, Pure, dependees, c)
                    }

                case FinalP(ImmutableContainerType) ⇒
                    Result(definedMethod, ImpureByAnalysis)

                // The type is at most conditionally immutable.
                case FinalP(_: TypeImmutability) ⇒ Result(definedMethod, ImpureByAnalysis)
                case FinalP(_: NonFinalField)    ⇒ Result(definedMethod, ImpureByAnalysis)

                case FinalP(CompileTimePure | Pure) ⇒
                    if (dependees.isEmpty)
                        Result(definedMethod, Pure)
                    else {
                        InterimResult(definedMethod, ImpureByAnalysis, Pure, dependees, c)
                    }

                case _: InterimEP[_, _] ⇒
                    dependees += eps
                    InterimResult(definedMethod, ImpureByAnalysis, Pure, dependees, c)

                case FinalP(_: Purity) ⇒
                    // a called method is impure...
                    Result(definedMethod, ImpureByAnalysis)
            }
        }

        InterimResult(definedMethod, ImpureByAnalysis, Pure, dependees, c)
    }

    def determinePurityStep1(definedMethod: DefinedMethod): ProperPropertyComputationResult = {
        val method = definedMethod.definedMethod

        // All parameters either have to be base types or have to be immutable.
        // IMPROVE Use plain object type once we use ObjectType in the store!
        var referenceTypes = method.parameterTypes.iterator.collect[ObjectType] {
            case t: ObjectType ⇒ t
            case _: ArrayType  ⇒ return Result(definedMethod, ImpureByAnalysis);
        }
        val methodReturnType = method.descriptor.returnType
        if (methodReturnType.isArrayType) {
            // we currently have no logic to decide whether the array was created locally
            // and did not escape or was created elsewhere...
            return Result(definedMethod, ImpureByAnalysis);
        }
        if (methodReturnType.isObjectType) {
            referenceTypes ++= Iterator(methodReturnType.asObjectType)
        }

        var dependees: Set[EOptionP[Entity, Property]] = Set.empty
        referenceTypes foreach { e ⇒
            propertyStore(e, TypeImmutability.key) match {
                case FinalP(ImmutableType) ⇒ /*everything is Ok*/
                case _: FinalEP[_, _] ⇒
                    return Result(definedMethod, ImpureByAnalysis);
                case InterimUBP(ub) if ub ne ImmutableType ⇒
                    return Result(definedMethod, ImpureByAnalysis);
                case epk ⇒ dependees += epk
            }
        }

        doDeterminePurityOfBody(definedMethod, dependees)
    }

    /**
     * Retrieves and commits the methods purity as calculated for its declaring class type for the
     * current DefinedMethod that represents the non-overwritten method in a subtype.
     */
    def baseMethodPurity(dm: DefinedMethod): ProperPropertyComputationResult = {

        def c(eps: SomeEOptionP): ProperPropertyComputationResult = eps match {
            case FinalP(p)                ⇒ Result(dm, p)
            case ep @ InterimLUBP(lb, ub) ⇒ InterimResult(dm, lb, ub, Seq(ep), c)

            case epk ⇒
                InterimResult(dm, ImpureByAnalysis, CompileTimePure, Seq(epk), c)
        }

        c(propertyStore(declaredMethods(dm.definedMethod), Purity.key))
    }

    /**
     * Determines the purity of the given method.
     */
    def determinePurity(definedMethod: DefinedMethod): ProperPropertyComputationResult = {
        val method = definedMethod.definedMethod

        // If thhis is not the method's declaration, but a non-overwritten method in a subtype,
        // don't re-analyze the code
        if (method.classFile.thisType ne definedMethod.declaringClassType)
            return baseMethodPurity(definedMethod);

        if (method.body.isEmpty)
            return Result(definedMethod, ImpureByAnalysis);

        if (method.isSynchronized)
            return Result(definedMethod, ImpureByAnalysis);

        // 1. step (will schedule 2. step if necessary):
        determinePurityStep1(definedMethod.asDefinedMethod)
    }

}

trait L0PurityAnalysisScheduler extends ComputationSpecification[FPCFAnalysis] {

    final def derivedProperty: PropertyBounds = PropertyBounds.lub(Purity)

    final override def uses: Set[PropertyBounds] = {
        Set(PropertyBounds.ub(TypeImmutability), PropertyBounds.ub(FieldMutability))
    }

}

object EagerL0PurityAnalysis
    extends L0PurityAnalysisScheduler
    with BasicFPCFEagerAnalysisScheduler {

    override def derivesEagerly: Set[PropertyBounds] = Set(derivedProperty)

    override def derivesCollaboratively: Set[PropertyBounds] = Set.empty

    override def start(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val analysis = new L0PurityAnalysis(p)
        val dms = p.get(DeclaredMethodsKey).declaredMethods
        val methodsWithBody = dms.toIterator.collect {
            case dm if dm.hasSingleDefinedMethod && dm.definedMethod.body.isDefined ⇒ dm.asDefinedMethod
        }
        ps.scheduleEagerComputationsForEntities(methodsWithBody)(analysis.determinePurity)
        analysis
    }
}

object LazyL0PurityAnalysis
    extends L0PurityAnalysisScheduler
    with BasicFPCFLazyAnalysisScheduler {

    override def derivesLazily: Some[PropertyBounds] = Some(derivedProperty)

    override def register(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val analysis = new L0PurityAnalysis(p)
        ps.registerLazyPropertyComputation(Purity.key, analysis.doDeterminePurity)
        analysis
    }
}
