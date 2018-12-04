/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses

import org.opalj.log.OPALLogger.{debug ⇒ trace}

import org.opalj.br.ClassFile
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject
import org.opalj.br.instructions.AASTORE
import org.opalj.br.instructions.ATHROW
import org.opalj.br.instructions.FieldWriteAccess
import org.opalj.br.instructions.INVOKEDYNAMIC
import org.opalj.br.instructions.INVOKEINTERFACE
import org.opalj.br.instructions.INVOKESPECIAL
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.instructions.INVOKESTATIC
import org.opalj.br.instructions.MethodInvocationInstruction
import org.opalj.br.instructions.PUTFIELD
import org.opalj.br.instructions.PUTSTATIC
import org.opalj.fpcf.properties.DoesNotLeakSelfReference
import org.opalj.fpcf.properties.LeaksSelfReference
import org.opalj.fpcf.properties.SelfReferenceLeakage

/**
 * A shallow analysis that computes the self reference leakage property.
 *
 * @author Michael Eichberg
 */
class L0SelfReferenceLeakageAnalysis(
        val project: SomeProject,
        val debug:   Boolean
) extends FPCFAnalysis {

    val SelfReferenceLeakage = org.opalj.fpcf.properties.SelfReferenceLeakage.Key

    /**
     * Determines for the given class file if any method may leak the self reference (`this`).
     *
     * Hence, it only makes sense to call this method if all supertypes do not leak
     * their self reference.
     */
    private[this] def determineSelfReferenceLeakageContinuation(
        classFile: ClassFile
    ): PropertyComputationResult = {

        val classHierarchy = project.classHierarchy

        val classType = classFile.thisType

        def thisIsSubtypeOf(otherType: ObjectType): Boolean = {
            classHierarchy.isASubtypeOf(classType, otherType.asObjectType).isYesOrUnknown
        }

        // This method just implements a very quick check if there is any potential
        // that the method may leak it's self reference. Hence, if this method returns
        // true, a more thorough analysis is useful/necessary.
        def potentiallyLeaksSelfReference(method: Method): Boolean = {
            val returnType = method.returnType
            if (returnType.isObjectType && thisIsSubtypeOf(returnType.asObjectType))
                return true;

            implicit val code = method.body.get
            val instructions = code.instructions
            val max = instructions.length
            var pc = 0
            while (pc < max) {
                val instruction = instructions(pc)
                instruction.opcode match {
                    case AASTORE.opcode ⇒
                        return true;
                    case ATHROW.opcode if thisIsSubtypeOf(ObjectType.Throwable) ⇒
                        // the exception may throw itself...
                        return true;
                    case INVOKEDYNAMIC.opcode ⇒
                        return true;
                    case INVOKEINTERFACE.opcode |
                        INVOKESPECIAL.opcode |
                        INVOKESTATIC.opcode |
                        INVOKEVIRTUAL.opcode ⇒
                        val invoke = instruction.asInstanceOf[MethodInvocationInstruction]
                        val parameterTypes = invoke.methodDescriptor.parameterTypes
                        if (parameterTypes.exists { pt ⇒ pt.isObjectType && thisIsSubtypeOf(pt.asObjectType) })
                            return true;
                    case PUTSTATIC.opcode | PUTFIELD.opcode ⇒
                        val fieldType = instruction.asInstanceOf[FieldWriteAccess].fieldType
                        if (fieldType.isObjectType && thisIsSubtypeOf(fieldType.asObjectType))
                            return true;
                    case _ ⇒ /*nothing to do*/
                }
                pc = instruction.indexOfNextInstruction(pc)
            }

            return false;
        }

        val doesLeakSelfReference =
            classFile.methods exists { m ⇒
                if (m.isNative || (
                    m.isNotStatic && m.isNotAbstract && potentiallyLeaksSelfReference(m)
                )) {
                    if (debug) {
                        trace("analysis result", m.toJava("leaks self reference"))
                    }
                    true
                } else {
                    if (debug) {
                        trace("analysis result", m.toJava("conceals self reference"))
                    }
                    false
                }
            }
        if (doesLeakSelfReference) {
            if (debug) {
                trace("analysis result", s"${classType.toJava} leaks its self reference")
            }
            Result(classType, LeaksSelfReference)
        } else {
            if (debug) {
                trace(
                    "analysis result",
                    s"${classType.toJava} does not leak its self reference"
                )
            }
            Result(classType, DoesNotLeakSelfReference)
        }
    }

    def determineSelfReferenceLeakage(classFile: ClassFile): PropertyComputationResult = {
        val classType = classFile.thisType
        if (classType eq ObjectType.Object) {
            if (debug) {
                trace(
                    "analysis result",
                    "java.lang.Object does not leak its self reference [configured]"
                )
            }
            return Result(classType /* <=> ObjectType.Object*/ , DoesNotLeakSelfReference);
        }

        // Let's check the direct supertypes w.r.t. their leakage property.
        val superClassType = classFile.superclassType.get
        val interfaceTypes = classFile.interfaceTypes

        // Given that we may have Java 8+, we may have a default method that leaks
        // the self reference.
        val superTypes: Seq[ObjectType] =
            if (superClassType == ObjectType.Object)
                interfaceTypes
            else
                interfaceTypes :+ superClassType

        if (debug)
            trace(
                "analysis progress",
                s"${classType.toJava} requiring leakage information about: ${superTypes.map(_.toJava).mkString(", ")}"
            )
        var dependees = Map.empty[Entity, EOptionP[Entity, Property]]
        propertyStore(superTypes, SelfReferenceLeakage) foreach {
            case epk @ EPK(e, _) ⇒ dependees += ((e, epk))

            case EPS(_, _ /*LeaksSelfReference*/ , LeaksSelfReference) ⇒
                return Result(classFile, LeaksSelfReference);

            case EPS(e, DoesNotLeakSelfReference, _ /*DoesNotLeakSelfReference*/ ) ⇒
            // nothing to do ...

            case eps @ EPS(e, _, _)                                                ⇒ dependees += ((e, eps))

        }

        // First, let's wait for the results for the supertypes...
        def c(eps: SomeEPS): PropertyComputationResult = {
            val EPS(e, lb, ub) = eps
            if (ub == LeaksSelfReference) {
                // ... we have a final result
                return Result(classType, LeaksSelfReference);
            }
            // Update dependee list...
            if (lb == DoesNotLeakSelfReference) {
                dependees -= e
            } else {
                dependees = (dependees - eps.e) + ((eps.e, eps))
            }

            if (dependees.isEmpty) {
                determineSelfReferenceLeakageContinuation(classFile)
            } else {
                InterimResult(classType, lb, ub, dependees.values, c)
            }
        }

        if (dependees.isEmpty) {
            determineSelfReferenceLeakageContinuation(classFile)
        } else {
            InterimResult(
                classFile,
                lb = LeaksSelfReference, ub = DoesNotLeakSelfReference,
                dependees.values,
                c
            )
        }

    }
}

object L0SelfReferenceLeakageAnalysis extends FPCFEagerAnalysisScheduler {

    override def uses: Set[PropertyKind] = Set.empty

    override def derives: Set[PropertyKind] = Set(SelfReferenceLeakage.Key)

    override type InitializationData = Null
    override def init(p: SomeProject, ps: PropertyStore): Null = null

    override def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    override def afterPhaseCompletion(p: SomeProject, ps: PropertyStore): Unit = {}

    /**
     * Starts the analysis for the given `project`. This method is typically implicitly
     * called by the [[FPCFAnalysesManager]].
     */
    override def start(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val config = p.config
        val debug = config.getBoolean("org.opalj.fcpf.analysis.L0SelfReferenceLeakage.debug")
        val analysis = new L0SelfReferenceLeakageAnalysis(p, debug)
        import analysis.determineSelfReferenceLeakage
        import p.allProjectClassFiles
        ps.scheduleEagerComputationsForEntities(allProjectClassFiles)(determineSelfReferenceLeakage)
        analysis
    }

}
