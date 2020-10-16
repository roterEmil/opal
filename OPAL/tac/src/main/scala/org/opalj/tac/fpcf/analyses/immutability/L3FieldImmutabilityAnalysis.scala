/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package immutability

import org.opalj.br.Attribute
import org.opalj.br.ClassSignature
import org.opalj.br.ClassTypeSignature
import org.opalj.br.Field
import org.opalj.br.ObjectType
import org.opalj.br.ProperTypeArgument
import org.opalj.br.RuntimeInvisibleAnnotationTable
import org.opalj.br.SimpleClassTypeSignature
import org.opalj.br.SourceFile
import org.opalj.br.TypeVariableSignature
import org.opalj.br.fpcf.properties.DeepImmutableField
import org.opalj.br.fpcf.properties.DeepImmutableType
import org.opalj.br.fpcf.properties.DependentImmutableField
import org.opalj.br.fpcf.properties.DependentImmutableType
import org.opalj.br.fpcf.properties.FieldImmutability
import org.opalj.br.fpcf.properties.ImmutableFieldReference
import org.opalj.br.fpcf.properties.LazyInitializedNotThreadSafeButDeterministicFieldReference
import org.opalj.br.fpcf.properties.LazyInitializedNotThreadSafeFieldReference
import org.opalj.br.fpcf.properties.LazyInitializedThreadSafeFieldReference
import org.opalj.br.fpcf.properties.MutableField
import org.opalj.br.fpcf.properties.MutableFieldReference
import org.opalj.br.fpcf.properties.MutableType
import org.opalj.br.fpcf.properties.FieldReferenceImmutability
import org.opalj.br.fpcf.properties.ShallowImmutableField
import org.opalj.br.fpcf.properties.ShallowImmutableType
import org.opalj.br.fpcf.properties.TypeImmutability
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.FinalEP
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.InterimResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.Result
import org.opalj.fpcf.SomeEPS
import org.opalj.br.Method
import org.opalj.br.fpcf.properties.EscapeProperty
import org.opalj.tac.fpcf.analyses.AbstractIFDSAnalysis.V
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.br.fpcf.properties.NoEscape
import org.opalj.tac.common.DefinitionSite
import org.opalj.tac.fpcf.properties.TACAI
import org.opalj.br.PCs
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.BasicFPCFEagerAnalysisScheduler
import org.opalj.br.fpcf.BasicFPCFLazyAnalysisScheduler
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.FPCFAnalysisScheduler
import org.opalj.br.fpcf.properties.ClassImmutability
import org.opalj.value.ASArrayValue
import org.opalj.br.fpcf.properties.DeepImmutableClass
import org.opalj.br.fpcf.properties.DependentImmutableClass
import org.opalj.br.fpcf.properties.MutableClass
import org.opalj.br.fpcf.properties.ShallowImmutableClass
import org.opalj.br.fpcf.properties.AtMost
import org.opalj.br.fpcf.properties.EscapeInCallee
import org.opalj.br.fpcf.properties.EscapeViaReturn
import org.opalj.fpcf.InterimEP
import org.opalj.fpcf.Entity
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyStore
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.FieldAccessInformationKey
import org.opalj.br.analyses.cg.ClosedPackagesKey
import org.opalj.br.analyses.cg.TypeExtensibilityKey
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.UBP
import org.opalj.tac.common.DefinitionSitesKey
import org.opalj.fpcf.InterimUBP
import org.opalj.br.ClassFile
import org.opalj.br.FormalTypeParameter

/**
 * Analysis that determines the immutability of org.opalj.br.Field
 *
 * @author Tobias Roth
 *
 */
class L3FieldImmutabilityAnalysis private[analyses] (val project: SomeProject) extends FPCFAnalysis {

    /**
     *  Describes the different kinds of dependent immutable fields, that depend on the concrete types that replace
     *  the generic parameters.
     *
     *  [[Dependent]] Shallow or mutable types could still exist.
     *  Example: Foo<T, MutableClass> f
     *
     *  [[NotShallowOrMutable]] There are no shallow and no mutable types.
     *  Example: Foo<T,T> f
     *
     *  [[OnlyDeepImmutable]] There are only deep immutable types and no generic parameters left.
     *  Example: Foo<String, String> f
     */
    sealed trait DependentImmutabilityKind
    case object Dependent extends DependentImmutabilityKind
    case object NotShallowOrMutable extends DependentImmutabilityKind
    case object OnlyDeepImmutable extends DependentImmutabilityKind

    case class State(
            field:                               Field,
            var typeIsImmutable:                 Boolean                                            = true,
            var referenceIsImmutable:            Option[Boolean]                                    = None,
            var noEscapePossibilityViaReference: Boolean                                            = true,
            var dependentImmutability:           DependentImmutabilityKind                          = OnlyDeepImmutable,
            var genericTypeSetNotDeepImmutable:  Boolean                                            = false,
            var tacDependees:                    Map[Method, (EOptionP[Method, TACAI], (PCs, PCs))] = Map.empty,
            var dependees:                       Set[EOptionP[Entity, Property]]                    = Set.empty,
            var innerArrayTypes:                 Set[ObjectType]                                    = Set.empty,
            var escapesStillDetermined:          Boolean                                            = false,
            var concreteClassTypeIsKnown:        Boolean                                            = false,
            var totalNumberOfFieldWrites:        Int                                                = 0
    ) {
        def hasDependees: Boolean = dependees.nonEmpty || tacDependees.nonEmpty

        def getDependees: Traversable[EOptionP[Entity, Property]] = dependees ++ tacDependees.valuesIterator.map(_._1)
    }

    final val typeExtensibility = project.get(TypeExtensibilityKey)
    final val closedPackages = project.get(ClosedPackagesKey)
    final val fieldAccessInformation = project.get(FieldAccessInformationKey)
    final val definitionSites = project.get(DefinitionSitesKey)
    implicit final val declaredMethods: DeclaredMethods = project.get(DeclaredMethodsKey)

    def doDetermineFieldImmutability(entity: Entity): PropertyComputationResult =
        entity match {
            case field: Field ⇒
                determineFieldImmutability(field)
            case _ ⇒
                val m = s"${entity.getClass.getName} is not an org.opalj.br.Field"
                throw new IllegalArgumentException(m)
        }

    private[analyses] def determineFieldImmutability(
        field: Field
    ): ProperPropertyComputationResult = {

        /**
         * Returns the formal type parameters from the class' and outer class' signature
         */
        def getFormalTypeParameters: Set[String] = {

            /**
             *
             * Extract the formal type parameters if it exist of a class attribute
             */
            def collectFormalTypeParameterFromClassAttribute(attribute: Attribute): Iterator[String] = attribute match {

                case ClassSignature(typeParameters, _, _) ⇒
                    typeParameters.iterator.map { case FormalTypeParameter(identifier, _, _) ⇒ identifier }

                case _ ⇒ Iterator.empty
            }

            /**
             * If the genericity is nested in an inner class
             * collect the generic type parameters from the field's outer classes
             */
            def getAllFormalParameters(classFile: ClassFile): Iterator[String] = {
                classFile.attributes.iterator.flatMap(collectFormalTypeParameterFromClassAttribute(_)) ++
                    {
                        if (classFile.outerType.isDefined) {
                            val outerClassFile = project.classFile(classFile.outerType.get._1)
                            if (outerClassFile.isDefined && outerClassFile.get != classFile) {
                                getAllFormalParameters(outerClassFile.get)
                            } else
                                Iterator.empty
                        } else
                            Iterator.empty
                    }
            }
            getAllFormalParameters(field.classFile).toSet
        }

        val classFormalTypeParameters: Set[String] = getFormalTypeParameters

        /**
         * Returns, if a generic parameter like e.g. 'T' is in the class' or an outer class' signature
         * @param string The generic type parameter that should be looked for
         */
        def isInClassesGenericTypeParameters(string: String): Boolean = classFormalTypeParameters.contains(string)

        /**
         * Determines the immutability of a fields type. Adjusts the state and registers the dependencies if necessary.
         *
         */
        def handleTypeImmutability(state: State): Unit = {
            val objectType = field.fieldType.asFieldType
            if (objectType == ObjectType.Object) {
                state.typeIsImmutable = false //handling generic fields
            } else if (objectType.isBaseType || objectType == ObjectType.String) {
                // base types are by design deep immutable
                //state.typeImmutability = true // true is default
            } else if (objectType.isArrayType) {
                // Because the entries of an array can be reassigned we state it as not being deep immutable
                state.typeIsImmutable = false
            } else {
                propertyStore(objectType, TypeImmutability.key) match {
                    case FinalP(DeepImmutableType) ⇒ // deep immutable type is set as default
                    case FinalP(DependentImmutableType) ⇒
                        state.typeIsImmutable = false
                    case UBP(ShallowImmutableType | MutableType) ⇒
                        state.typeIsImmutable = false
                        if (field.fieldType != ObjectType.Object)
                            state.dependentImmutability = Dependent
                    case epk ⇒ state.dependees += epk
                }
            }
        }

        def handleGenericity()(implicit state: State): Unit = {
            var noShallowOrMutableTypesInGenericTypeFound = true
            var onlyDeepImmutableTypesInGenericTypeFound = true
            var genericParameters: List[ObjectType] = List()
            var noRelevantAttributesFound = true
            state.field.attributes.foreach {

                case RuntimeInvisibleAnnotationTable(_) | SourceFile(_) ⇒

                case TypeVariableSignature(t) ⇒
                    noRelevantAttributesFound = false
                    onlyDeepImmutableTypesInGenericTypeFound = false
                    if (!isInClassesGenericTypeParameters(t))
                        noShallowOrMutableTypesInGenericTypeFound = false

                case ClassTypeSignature(_, SimpleClassTypeSignature(_, typeArguments), _) ⇒
                    noRelevantAttributesFound = false
                    typeArguments.foreach {

                        case ProperTypeArgument(_, TypeVariableSignature(identifier)) ⇒
                            onlyDeepImmutableTypesInGenericTypeFound = false
                            if (!isInClassesGenericTypeParameters(identifier))
                                noShallowOrMutableTypesInGenericTypeFound = false

                        case ProperTypeArgument(_, ClassTypeSignature(outerPackageIdentifier,
                            SimpleClassTypeSignature(innerPackageIdentifier, _), _)) ⇒
                            val objectPath = outerPackageIdentifier match {
                                case Some(prepackageIdentifier) ⇒ prepackageIdentifier + innerPackageIdentifier
                                case _                          ⇒ innerPackageIdentifier
                            }
                            genericParameters ::= ObjectType(objectPath)

                        case _ ⇒
                            noShallowOrMutableTypesInGenericTypeFound = false
                            onlyDeepImmutableTypesInGenericTypeFound = false
                    }
                case _ ⇒
                    noShallowOrMutableTypesInGenericTypeFound = false
                    onlyDeepImmutableTypesInGenericTypeFound = false
            }

            genericParameters.foreach(objectType ⇒ {
                propertyStore(objectType, TypeImmutability.key) match {

                    case FinalP(DeepImmutableType) ⇒ //nothing to do here: default value is deep immutable

                    case FinalP(DependentImmutableType) ⇒
                        onlyDeepImmutableTypesInGenericTypeFound = false
                        state.typeIsImmutable = false

                    case UBP(ShallowImmutableType | MutableType) ⇒
                        noShallowOrMutableTypesInGenericTypeFound = false
                        onlyDeepImmutableTypesInGenericTypeFound = false
                        state.typeIsImmutable = false

                    case ep ⇒ state.dependees += ep
                }
            })

            //Prevents the case of keeping the default values of these
            // flags only because of no relevant attribute has been found
            if (!noRelevantAttributesFound) {
                if (onlyDeepImmutableTypesInGenericTypeFound) {
                    //nothing to do...
                } else if (noShallowOrMutableTypesInGenericTypeFound &&
                    state.dependentImmutability != Dependent) {
                    state.dependentImmutability = NotShallowOrMutable
                } else
                    state.dependentImmutability = Dependent

            } else
                state.dependentImmutability = Dependent
        }

        /**
         * Returns the TACode for a method if available, registering dependencies as necessary.
         */
        def getTACAI(
            method: Method,
            pcs:    PCs,
            isRead: Boolean
        )(implicit state: State): Option[TACode[TACMethodParameter, V]] = {
            propertyStore(method, TACAI.key) match {

                case finalEP: FinalEP[Method, TACAI] ⇒ finalEP.ub.tac

                case epk ⇒
                    var reads = IntTrieSet.empty
                    var writes = IntTrieSet.empty
                    if (state.tacDependees.contains(method)) {
                        reads = state.tacDependees(method)._2._1
                        writes = state.tacDependees(method)._2._2
                    }
                    if (isRead) {
                        state.tacDependees += method -> ((epk, (reads ++ pcs, writes)))
                    }
                    if (!isRead) {
                        state.tacDependees += method -> ((epk, (reads, writes ++ pcs)))
                    }
                    None
            }
        }

        /**
         * Handles the influence of an escape property on the field mutability.
         * @return true if the object - on which a field write occurred - escapes, false otherwise.
         * @note (Re-)Adds dependees as necessary.
         */
        def doesItEscapeViaMethod(
            ep: EOptionP[DefinitionSite, EscapeProperty]
        )(implicit state: State): Boolean = {
            ep match {
                case FinalP(NoEscape) ⇒ false

                case InterimUBP(NoEscape) ⇒
                    state.dependees += ep
                    false

                case UBP(EscapeInCallee | EscapeViaReturn)        ⇒ true
                case FinalP(AtMost(_))                            ⇒ true
                case _: FinalEP[DefinitionSite, EscapeProperty]   ⇒ true // Escape state is worse than via return
                case InterimUBP(AtMost(_))                        ⇒ true
                case _: InterimEP[DefinitionSite, EscapeProperty] ⇒ true // Escape state is worse than via return

                case epk ⇒
                    state.dependees += epk
                    false
            }
        }

        /**
         * Determine if the referenced object can escape either via field reads or writes.
         */
        def determineEscapeOfReferencedObjectOrValue()(implicit state: State): Unit = {
            state.escapesStillDetermined = true
            state.noEscapePossibilityViaReference = state.field.isPrivate

            if (state.noEscapePossibilityViaReference) {
                val writes = fieldAccessInformation.writeAccesses(state.field)

                //has to be determined before the following foreach loop because the information is needed
                state.totalNumberOfFieldWrites = writes.foldLeft(0)(_ + _._2.size)
                writes.foreach(writeAccess ⇒ {
                    val (method, pcs) = writeAccess
                    checkFieldWritesForEffImmutability(method, pcs)
                })
                if (state.noEscapePossibilityViaReference) {
                    val reads = fieldAccessInformation.readAccesses(state.field)
                    reads.foreach(read ⇒ {
                        val (method, pcs) = read
                        determineEscapeViaFieldReads(method, pcs)
                    })

                }
            }
        }

        /**
         * Determine if the referenced object can escape via field reads.
         */
        def determineEscapeViaFieldReads(method: Method, pcs: PCs)(implicit state: State): Unit = {

            val taCodeOption = getTACAI(method, pcs, isRead = true)
            // if the taCode is not defined this function is again called in the continuation function
            if (taCodeOption.isDefined) {
                val taCode = taCodeOption.get
                if (pcs.exists { pc ⇒
                    val readIndex = taCode.pcToIndex(pc)
                    // This if-statement is necessary, because there are -1 elements in the array
                    if (readIndex != -1) {
                        val stmt = taCode.stmts(readIndex)
                        if (stmt.isAssignment) {
                            val assignment = stmt.asAssignment
                            /*if (doesItEscapeViaMethod(
                                propertyStore(definitionSites(method, assignment.pc), EscapeProperty.key)
                            )) {
                                state.noEscapePossibilityViaReference = false
                                println("false1")
                                return ;
                            } else */

                            assignment.targetVar.usedBy.exists(useSite ⇒ {

                                val fieldsUseSiteStmt = taCode.stmts(useSite)

                                if (fieldsUseSiteStmt.isAssignment) {

                                    val assignment = fieldsUseSiteStmt.asAssignment

                                    if (assignment.expr.isVirtualFunctionCall ||
                                        assignment.expr.isStaticFunctionCall) {
                                        val functionCall = assignment.expr.asFunctionCall
                                        field.fieldType.isObjectType && functionCall.params.exists(!_.isConst)

                                    } else if (assignment.expr.isArrayLoad) {
                                        val arrayLoad = assignment.expr.asArrayLoad
                                        arrayLoad.arrayRef.asVar.value.toCanonicalForm match {
                                            case value: ASArrayValue ⇒
                                                val innerArrayType = value.theUpperTypeBound.componentType
                                                if (innerArrayType.isBaseType) {
                                                    false // nothing to do, because it can not be mutated
                                                } else if (innerArrayType.isArrayType) {
                                                    true // to be sound
                                                } else if (innerArrayType.isObjectType) {
                                                    //If a deep immutable object escapes, it can not be mutated
                                                    propertyStore(innerArrayType, TypeImmutability.key) match {

                                                        case FinalP(DeepImmutableType) ⇒ false //nothing to do

                                                        case UBP(DependentImmutableType | ShallowImmutableType |
                                                            MutableType) ⇒
                                                            true

                                                        case ep ⇒
                                                            state.innerArrayTypes += innerArrayType.asObjectType
                                                            state.dependees += ep
                                                            false
                                                    }
                                                } else true
                                            case _ ⇒ true
                                        }
                                    } else true
                                } else
                                    !fieldsUseSiteStmt.isMonitorEnter &&
                                        !fieldsUseSiteStmt.isMonitorExit &&
                                        !fieldsUseSiteStmt.isIf
                            })
                        } else
                            //nothing to do in case of Expr; The value is only read but not assigned to another one
                            !stmt.isExprStmt
                    } else false //nothing to do; -1 means NOTHING as a placeholder
                }) {
                    state.noEscapePossibilityViaReference = false
                }
            }
        }

        /**
         * Determine if the referenced object can escape via field writes
         */
        def checkFieldWritesForEffImmutability(method: Method, pcs: PCs)(implicit state: State): Unit = {

            //Needed because of cyclic calls of the functions - to prevent infinite cycles
            var seen: Set[Stmt[V]] = Set.empty

            /**
             * Checks if the parameters of a static function call are no parameters from an outer
             * function and are constants
             */
            def doesStaticFunctionCallEnableEscape(
                staticFunctionCall: StaticFunctionCall[V],
                tacCode:            TACode[TACMethodParameter, V]
            ): Boolean = staticFunctionCall.params.exists(_.asVar.definedBy.exists(
                definedByIndex ⇒ definedByIndex < 0 ||
                    !tacCode.stmts(definedByIndex).asAssignment.expr.isConst
            ))

            /**
             * In case of the concrete assigned classtype is known this method handles the immutability of it.
             * @note [[state.concreteClassTypeIsKnown]] must be set to true, when calling this function
             */
            def handleKnownClassType(objectType: ObjectType)(implicit state: State): Unit = {

                propertyStore(objectType, ClassImmutability.key) match {

                    case UBP(MutableClass | ShallowImmutableClass | DependentImmutableClass) ⇒
                        state.typeIsImmutable = false

                    case FinalP(DeepImmutableClass) ⇒
                        state.typeIsImmutable = true

                    case eps ⇒
                        state.dependees += eps
                }
            }

            /**
             * Checks if the referenced object or elements from it can escape via the nonvirtualmethod-call
             */
            def doesNonVirtualMethodCallEnablesEscape(
                method:               Method,
                nonVirtualMethodCall: NonVirtualMethodCall[V],
                tacCode:              TACode[TACMethodParameter, V]
            )(implicit state: State): Boolean =
                nonVirtualMethodCall.params.exists {
                    param ⇒
                        param.asVar.definedBy.exists {
                            paramDefinedByIndex ⇒
                                if (paramDefinedByIndex < 0) {
                                    true
                                } else {
                                    val paramDefinitionStmt = tacCode.stmts(paramDefinedByIndex)
                                    if (paramDefinitionStmt.isAssignment) {
                                        val assignmentExpression = paramDefinitionStmt.asAssignment.expr
                                        if (assignmentExpression.isGetField ||
                                            assignmentExpression.isGetStatic) {
                                            var assignedField: Option[Field] = None
                                            if (assignmentExpression.isGetField)
                                                assignedField = assignmentExpression.asGetField.resolveField
                                            else if (assignmentExpression.isGetStatic)
                                                assignedField = assignmentExpression.asGetStatic.resolveField

                                            if (assignedField.isDefined && assignedField.get != state.field) {
                                                propertyStore(assignedField.get, FieldImmutability.key) match {
                                                    case FinalP(DeepImmutableField) ⇒ false //nothing to do here
                                                    case FinalP(_) ⇒
                                                        true

                                                    case ep ⇒
                                                        state.dependees += ep
                                                        false
                                                }
                                            } else false
                                        } else if (assignmentExpression.isVirtualFunctionCall) {
                                            val virtualFunctionCall = assignmentExpression.asVirtualFunctionCall
                                            virtualFunctionCall.params.exists(
                                                param ⇒ param.asVar.definedBy.head < 0 ||
                                                    !tacCode.stmts(param.asVar.definedBy.head).asAssignment.expr.isConst
                                            )
                                        } else if (assignmentExpression.isStaticFunctionCall) {
                                            doesStaticFunctionCallEnableEscape(assignmentExpression.asStaticFunctionCall, tacCode)
                                        } else if (assignmentExpression.isNew) {
                                            val newStmt = assignmentExpression.asNew
                                            if (field.fieldType.isObjectType &&
                                                newStmt.tpe.mostPreciseObjectType == field.fieldType.asObjectType &&
                                                state.totalNumberOfFieldWrites == 1) {
                                                state.concreteClassTypeIsKnown = true
                                                handleKnownClassType(newStmt.tpe.mostPreciseObjectType)
                                            }

                                            paramDefinitionStmt.asAssignment.targetVar.asVar.usedBy.exists { usedSiteIndex ⇒
                                                val stmt = tacCode.stmts(usedSiteIndex)
                                                if (stmt.isNonVirtualMethodCall) {
                                                    if (!seen.contains(stmt)) {
                                                        seen += stmt
                                                        doesNonVirtualMethodCallEnablesEscape(method, stmt.asNonVirtualMethodCall, tacCode)
                                                    } else false
                                                } else true
                                            }

                                        } else
                                            assignmentExpression.isConst
                                    } else {
                                        val definitionSitesOfParam = definitionSites(method, paramDefinedByIndex)
                                        val stmt = tacCode.stmts(tacCode.pcToIndex(definitionSitesOfParam.pc))
                                        if (stmt.isNonVirtualMethodCall) {
                                            if (!seen.contains(stmt)) {
                                                seen += stmt
                                                doesNonVirtualMethodCallEnablesEscape(method, stmt.asNonVirtualMethodCall, tacCode)
                                            } else false
                                        } else if (stmt.isPutField || stmt.isPutStatic) {
                                            if (!seen.contains(stmt)) {
                                                seen += stmt
                                                doesPutEnableEscape(stmt, method, tacCode)
                                            } else false
                                        } else if (stmt.isArrayStore) {
                                            true //TODO handling that case more precise
                                        } else { // other cases that the purity analysis can not handle
                                            doesItEscapeViaMethod(propertyStore(definitionSitesOfParam, EscapeProperty.key))
                                            //false
                                        }
                                    } //TODO go further
                                }
                        }
                }

            /**
             * Checks if a reference object can escape via a given putfield or putstatic
             */
            def doesPutEnableEscape(putStmt: Stmt[V], method: Method, tacCode: TACode[TACMethodParameter, V]): Boolean = {

                val (putDefinitionSites, putValue) = {
                    if (putStmt.isPutField) {
                        val putField = putStmt.asPutField
                        (putField.value.asVar.definedBy, putField.value.asVar)
                    } else if (putStmt.isPutStatic) {
                        val putStatic = putStmt.asPutStatic
                        (putStatic.value.asVar.definedBy, putStatic.value.asVar)
                    } else {
                        //state.noEscapePossibilityViaReference = false
                        return true;
                    }
                }

                val putValueDefinedByIndex = putValue.definedBy.head
                if (putValue.value.isArrayValue.isYes) {

                    if (putValueDefinedByIndex >= 0) { //necessary
                        tacCode.stmts(putValueDefinedByIndex).asAssignment.targetVar.usedBy.exists { usedByIndex ⇒
                            val arrayStmt = tacCode.stmts(usedByIndex)
                            if (arrayStmt != putStmt) {
                                if (arrayStmt.isArrayStore) {
                                    val arrayStore = arrayStmt.asArrayStore
                                    val arrayStoreIndex = arrayStore.index
                                    val isArrayIndexConst =
                                        tacCode.stmts(arrayStoreIndex.asVar.definedBy.head).asAssignment.expr.isConst
                                    val assignedValue = arrayStore.value
                                    if (assignedValue.asVar.definedBy.head >= 0) {
                                        val valueAssignment = tacCode.stmts(assignedValue.asVar.definedBy.head).asAssignment
                                        val assignedExpr = valueAssignment.expr
                                        val useSites = valueAssignment.targetVar.usedBy.map(tacCode.stmts(_))
                                        useSites.exists { useSite ⇒
                                            if (useSite.isNonVirtualMethodCall) {
                                                val nonVirtualMethodCall = useSite.asNonVirtualMethodCall
                                                nonVirtualMethodCall.params.exists(param ⇒
                                                    !param.isConst &&
                                                        param.asVar.definedBy.head > -1 && //TODO look at more than the head
                                                        !tacCode.stmts(param.asVar.definedBy.head).asAssignment.expr.isConst)
                                            } else if (useSite == arrayStore) {
                                                false
                                            } else if (useSite.isReturnValue) {
                                                //assigned array-element escapes
                                                true
                                            } else {
                                                true //to be sound
                                            }
                                        }

                                        if (!isArrayIndexConst) {
                                            true
                                        } else if (assignedExpr.isStaticFunctionCall) {
                                            doesStaticFunctionCallEnableEscape(assignedExpr.asStaticFunctionCall, tacCode)
                                        } else if (assignedExpr.isNew) {
                                            val newStmt = assignedExpr.asNew

                                            if (field.fieldType.isObjectType &&
                                                newStmt.tpe.mostPreciseObjectType == field.fieldType.asObjectType &&
                                                state.totalNumberOfFieldWrites == 1) {
                                                state.concreteClassTypeIsKnown = true
                                                handleKnownClassType(newStmt.tpe.mostPreciseObjectType)
                                            }

                                            valueAssignment.targetVar.asVar.usedBy.exists {
                                                index ⇒
                                                    val tmpStmt = tacCode.stmts(index)
                                                    if (tmpStmt.isArrayStore) {
                                                        false // can be ingored
                                                    } else if (tmpStmt.isNonVirtualMethodCall) {
                                                        val nonVirtualMethodcall = tmpStmt.asNonVirtualMethodCall
                                                        if (!seen.contains(tmpStmt)) {
                                                            seen += tmpStmt
                                                            doesNonVirtualMethodCallEnablesEscape(method, nonVirtualMethodcall, tacCode)
                                                        }
                                                        false //nothing to do in the else case. Stmt has still been handled
                                                    } else true
                                            }
                                        } else true
                                    } else true
                                } else true
                            } else false
                        }
                    } else true
                } else {

                    putDefinitionSites.exists { putDefinitionSite ⇒
                        if (putDefinitionSite >= 0) { //necessary
                            val definitionSiteStatement = tacCode.stmts(putDefinitionSite)
                            //println("def site stmt: "+definitionSiteStatement)
                            val definitionSiteAssignment = definitionSiteStatement.asAssignment
                            //println("def site assignement: "+definitionSiteAssignment)
                            if (definitionSiteAssignment.expr.isStaticFunctionCall) {
                                // println("handle static function call")
                                doesStaticFunctionCallEnableEscape(definitionSiteAssignment.expr.asStaticFunctionCall, tacCode)

                            } else if (definitionSiteAssignment.expr.isVar) {
                                //  println("def site is var")
                                val definitionSiteVar = definitionSiteAssignment.expr.asVar
                                definitionSiteVar.usedBy.exists {
                                    definitionSiteVarUseSite ⇒
                                        val definitionSiteVarUseSiteStmt = tacCode.stmts(definitionSiteVarUseSite)
                                        if (definitionSiteVarUseSiteStmt.isNonVirtualMethodCall) {
                                            val nonVirtualMethodCall = definitionSiteVarUseSiteStmt.asNonVirtualMethodCall
                                            doesNonVirtualMethodCallEnablesEscape(method, nonVirtualMethodCall, tacCode)
                                        } else true
                                    //TODO handle all cases
                                }

                                /*for (definitionSiteVarUseSite ← definitionSiteVar.usedBy) {

                                } */
                            } else if (definitionSiteAssignment.expr.isNew) {

                                val newStmt = definitionSiteAssignment.expr.asNew
                                if (field.fieldType.isObjectType &&
                                    newStmt.tpe.mostPreciseObjectType == field.fieldType.asObjectType &&
                                    state.totalNumberOfFieldWrites == 1) {
                                    state.concreteClassTypeIsKnown = true
                                    handleKnownClassType(newStmt.tpe.mostPreciseObjectType)
                                }
                                if (!method.isConstructor) {
                                    definitionSiteAssignment.targetVar.asVar.usedBy.exists {
                                        x ⇒
                                            val tmpStmt = tacCode.stmts(x)
                                            if (tmpStmt.isPutStatic || tmpStmt.isPutField) {
                                                if (!seen.contains(tmpStmt)) {
                                                    seen += tmpStmt
                                                    doesPutEnableEscape(tmpStmt, method, tacCode)
                                                } else false
                                            } else if (tmpStmt.isNonVirtualMethodCall) {
                                                if (!seen.contains(tmpStmt)) {
                                                    seen += tmpStmt
                                                    doesNonVirtualMethodCallEnablesEscape(method, tmpStmt.asNonVirtualMethodCall, tacCode)
                                                } else false
                                            } else true
                                    }
                                } else {
                                    definitionSiteAssignment.targetVar.usedBy.exists { useSite ⇒
                                        val useSiteStmt = tacCode.stmts(useSite)
                                        if (useSiteStmt.isNonVirtualMethodCall) {
                                            doesNonVirtualMethodCallEnablesEscape(method, useSiteStmt.asNonVirtualMethodCall, tacCode)
                                        } else if (useSiteStmt.isPutStatic || useSiteStmt.isPutField) {
                                            if (!seen.contains(useSiteStmt)) {
                                                seen += useSiteStmt
                                                doesPutEnableEscape(useSiteStmt, method, tacCode)
                                            } else false
                                        } else if (useSiteStmt.isAssignment) {
                                            true //TODO
                                        } else {
                                            true
                                        }
                                    }
                                }
                                //TODO handle all cases!!
                            } else !definitionSiteAssignment.expr.isConst
                        } else true
                    }
                }
            }

            /**
             * Begin of method check field writes
             */
            val tacCodeOption = getTACAI(method, pcs, false)
            if (tacCodeOption.isDefined) {
                val taCode = tacCodeOption.get
                if (pcs.exists { pc ⇒
                    val index = taCode.pcToIndex(pc)
                    if (index >= 0) {
                        val stmt = taCode.stmts(index)
                        if (!seen.contains(stmt)) {
                            seen += stmt
                            doesPutEnableEscape(stmt, method, taCode)
                        } else false
                    } else true
                })
                    state.noEscapePossibilityViaReference = false
            }
        }

        /**
         * If there are no dependencies left, this method can be called to create the result.
         */
        def createResult(implicit state: State): ProperPropertyComputationResult = {
            if (state.hasDependees) {
                val lowerBound =
                    if (state.referenceIsImmutable.isDefined && state.referenceIsImmutable.get)
                        ShallowImmutableField
                    else
                        MutableField
                val upperBound = {
                    if (state.referenceIsImmutable.isEmpty)
                        DeepImmutableField
                    else {
                        state.referenceIsImmutable match {

                            case Some(false) | None ⇒ MutableField

                            case Some(true) ⇒
                                if (state.tacDependees.isEmpty && !state.concreteClassTypeIsKnown) {
                                    if (state.typeIsImmutable) {
                                        DeepImmutableField
                                    } else {
                                        if (state.noEscapePossibilityViaReference) {
                                            DeepImmutableField
                                        } else {
                                            state.dependentImmutability match {
                                                case NotShallowOrMutable ⇒ DependentImmutableField
                                                case OnlyDeepImmutable   ⇒ DeepImmutableField
                                                case _                   ⇒ ShallowImmutableField

                                            }
                                        }
                                    }
                                } else
                                    DeepImmutableField
                        }
                    }

                } //TODO check
                DeepImmutableField
                InterimResult(
                    field,
                    lowerBound,
                    upperBound,
                    state.getDependees,
                    c
                )
            } else {
                if (!state.escapesStillDetermined)
                    state.noEscapePossibilityViaReference = false
                state.referenceIsImmutable match {

                    case Some(false) | None ⇒ Result(field, MutableField)

                    case Some(true) ⇒
                        if (state.typeIsImmutable) {
                            Result(field, DeepImmutableField)
                        } else {
                            if (state.noEscapePossibilityViaReference) {
                                Result(field, DeepImmutableField)
                            } else {
                                state.dependentImmutability match {
                                    case NotShallowOrMutable ⇒ Result(field, DependentImmutableField)
                                    case OnlyDeepImmutable   ⇒ Result(field, DeepImmutableField)
                                    case _                   ⇒ Result(field, ShallowImmutableField)
                                }
                            }
                        }
                }
            }
        }

        def c(eps: SomeEPS)(implicit state: State): ProperPropertyComputationResult = {
            import org.opalj.fpcf.EUBP
            if (eps.asEPS.pk != TACAI.key)
                state.dependees = state.dependees.filter(_.e ne eps.e)
            eps match {
                case FinalP(DeepImmutableType) ⇒ //nothing to do -> is default

                case EUBP(t, MutableType | ShallowImmutableType) ⇒
                    state.typeIsImmutable = false
                    if (t != ObjectType.Object) { // in case of generic fields
                        state.dependentImmutability = Dependent
                    }
                    t match {
                        case objectType: ObjectType if state.innerArrayTypes.contains(objectType) ⇒
                            state.noEscapePossibilityViaReference = false
                        case _ ⇒
                    }

                case FinalEP(t, DependentImmutableType) ⇒
                    state.typeIsImmutable = false
                    if (t != field.fieldType && state.dependentImmutability == OnlyDeepImmutable)
                        state.dependentImmutability = NotShallowOrMutable
                    t match {
                        case objectType: ObjectType if state.innerArrayTypes.contains(objectType) ⇒
                            state.noEscapePossibilityViaReference = false
                        case _ ⇒
                    }

                case UBP(MutableFieldReference | LazyInitializedNotThreadSafeFieldReference) ⇒
                    state.typeIsImmutable = false
                    state.referenceIsImmutable = Some(false)
                    return Result(field, MutableField);

                case FinalP(ImmutableFieldReference | LazyInitializedThreadSafeFieldReference |
                    LazyInitializedNotThreadSafeButDeterministicFieldReference) ⇒
                    state.referenceIsImmutable = Some(true)

                case FinalP(DeepImmutableField) ⇒ // nothing to do

                case UBP(DependentImmutableField | ShallowImmutableField | MutableField) ⇒
                    state.noEscapePossibilityViaReference = false

                case eps if eps.asEPS.pk == TACAI.key ⇒
                    val newEP = eps.asInstanceOf[EOptionP[Method, TACAI]]
                    val method = newEP.e
                    state.tacDependees -= method
                    val pcs = state.tacDependees(method)._2
                    if (eps.isFinal) {
                        checkFieldWritesForEffImmutability(method, pcs._2)(state)
                        determineEscapeViaFieldReads(method, pcs._1)(state)
                    } else {
                        state.tacDependees += method -> ((newEP, pcs))
                    }

                case eps if eps.isFinal && eps.asEPS.pk == EscapeProperty.key ⇒
                    if (doesItEscapeViaMethod(eps.asInstanceOf[EOptionP[DefinitionSite, EscapeProperty]])(state))
                        state.noEscapePossibilityViaReference = false

                case FinalP(DeepImmutableClass) ⇒
                    state.typeIsImmutable = true

                case UBP(ShallowImmutableClass | DependentImmutableClass | MutableClass) ⇒
                    state.typeIsImmutable = false

                case eps ⇒
                    state.dependees += eps
            }
            createResult
        }

        /**
         * Requests the immutability of the field reference and registers the dependees if necessary
         */
        implicit val state: State = State(field)
        propertyStore(field, FieldReferenceImmutability.key) match {
            case FinalP(ImmutableFieldReference) ⇒ state.referenceIsImmutable = Some(true)
            case FinalP(LazyInitializedThreadSafeFieldReference |
                LazyInitializedNotThreadSafeButDeterministicFieldReference) ⇒
                state.referenceIsImmutable = Some(true)
            case FinalP(MutableFieldReference | LazyInitializedNotThreadSafeFieldReference) ⇒
                return Result(field, MutableField);
            case ep ⇒
                state.dependees += ep
        }
        // println("A")
        /**
         * Determines whether the field is dependent immutable
         */
        handleGenericity()
        // println("B")
        /**
         * Determines whether the reference object escapes or can be mutated.
         */
        if (state.referenceIsImmutable.isEmpty || state.referenceIsImmutable.get) {
            determineEscapeOfReferencedObjectOrValue()
        }
        // println("c")
        /**
         * In cases where we know the concrete class type assigned to the field we could use the immutabiltiy of this.
         */
        if (!state.concreteClassTypeIsKnown) {
            // println("C1")
            handleTypeImmutability(state)
        }
        // println("D")
        createResult
    }
}

trait L3FieldImmutabilityAnalysisScheduler extends FPCFAnalysisScheduler {

    import org.opalj.fpcf.PropertyBounds

    final override def uses: Set[PropertyBounds] = Set(
        PropertyBounds.ub(TACAI),
        PropertyBounds.ub(EscapeProperty),
        PropertyBounds.lub(FieldReferenceImmutability),
        PropertyBounds.lub(TypeImmutability),
        PropertyBounds.lub(FieldImmutability)
    )

    final def derivedProperty: PropertyBounds = PropertyBounds.lub(FieldImmutability)
}

/**
 *
 * Executor for the eager field immutability analysis.
 *
 */
object EagerL3FieldImmutabilityAnalysis extends L3FieldImmutabilityAnalysisScheduler
    with BasicFPCFEagerAnalysisScheduler {

    override def derivesEagerly: Set[PropertyBounds] = Set(derivedProperty)

    override def derivesCollaboratively: Set[PropertyBounds] = Set.empty

    final override def start(p: SomeProject, ps: PropertyStore, unused: Null): FPCFAnalysis = {
        val analysis = new L3FieldImmutabilityAnalysis(p)
        val fields = p.allFields // p.allProjectClassFiles.flatMap(classfile ⇒ classfile.fields) // p.allFields
        ps.scheduleEagerComputationsForEntities(fields)(analysis.determineFieldImmutability)
        analysis
    }
}

/**
 *
 * Executor for the lazy field immutability analysis.
 *
 */
object LazyL3FieldImmutabilityAnalysis extends L3FieldImmutabilityAnalysisScheduler
    with BasicFPCFLazyAnalysisScheduler {

    override def derivesLazily: Some[PropertyBounds] = Some(derivedProperty)

    final override def register(
        p:      SomeProject,
        ps:     PropertyStore,
        unused: Null
    ): FPCFAnalysis = {
        val analysis = new L3FieldImmutabilityAnalysis(p)
        ps.registerLazyPropertyComputation(
            FieldImmutability.key,
            analysis.determineFieldImmutability
        )
        analysis
    }
}
