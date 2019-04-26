/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import org.opalj.collection.immutable.IntTrieSet
import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPK
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.InterimEUBP
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.NoResult
import org.opalj.fpcf.PartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEPS
import org.opalj.fpcf.UBP
import org.opalj.fpcf.UBPS
import org.opalj.value.ValueInformation
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.FPCFTriggeredAnalysisScheduler
import org.opalj.br.fpcf.cg.properties.CallersProperty
import org.opalj.br.fpcf.cg.properties.NoCallers
import org.opalj.br.fpcf.pointsto.properties.PointsTo
import org.opalj.br.DefinedMethod
import org.opalj.br.Method
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.fpcf.cg.properties.Callees
import org.opalj.br.ObjectType
import org.opalj.br.analyses.VirtualFormalParameters
import org.opalj.br.analyses.VirtualFormalParametersKey
import org.opalj.br.fpcf.cg.properties.NoCallees
import org.opalj.tac.common.DefinitionSites
import org.opalj.tac.common.DefinitionSitesKey
import org.opalj.tac.fpcf.properties.TACAI

class PointsToState private (
        private[pointsto] val method:   DefinedMethod,
        private[this] var _tacDependee: Option[EOptionP[Method, TACAI]]
) {

    /*private[this]*/ val _pointsToSets: mutable.Map[Entity, UIDSet[ObjectType]] = mutable.Map.empty

    //val localPointsToSets: mutable.Map[DefinitionSite, UIDSet[ObjectType]] = mutable.Map.empty

    // todo document
    // if we get an update for e: we have to update all points-to sets for the entities in map(e)
    private[this] val _dependeeToDependers: mutable.Map[Entity, mutable.Set[Entity]] = mutable.Map.empty

    private[this] val _dependerToDependees: mutable.Map[Entity, mutable.Set[Entity]] = mutable.Map.empty

    // todo accessor methods
    private[pointsto] val _dependees: mutable.Map[Entity, EOptionP[Entity, PointsTo]] = mutable.Map.empty

    private[this] var _calleesDependee: Option[EOptionP[DeclaredMethod, Callees]] = None

    def callees(ps: PropertyStore): Callees = {
        val calleesProperty = if (_calleesDependee.isDefined) {

            _calleesDependee.get
        } else {
            val calleesProperty = ps(method, Callees.key)
            _calleesDependee = Some(calleesProperty)
            calleesProperty
        }

        if (calleesProperty.isEPK)
            NoCallees
        else calleesProperty.ub
    }

    def updateCalleesDependee(newCallees: EPS[DeclaredMethod, Callees]): Unit = {
        _calleesDependee = Some(newCallees)
    }

    def tac: TACode[TACMethodParameter, DUVar[ValueInformation]] = {
        assert(_tacDependee.isDefined)
        assert(_tacDependee.get.ub.tac.isDefined)
        _tacDependee.get.ub.tac.get
    }

    def dependees: Iterable[EOptionP[Entity, Property]] = {
        _tacDependee.filterNot(_.isFinal) ++ _dependees.values ++ _calleesDependee.filter(_.isRefinable)
    }

    def hasOpenDependees: Boolean = {
        !_tacDependee.forall(_.isFinal) ||
            _dependees.nonEmpty ||
            (_calleesDependee.isDefined && _calleesDependee.get.isRefinable)
    }

    def updateTACDependee(tacDependee: EOptionP[Method, TACAI]): Unit = {
        _tacDependee = Some(tacDependee)
    }

    def addPointsToDependency(depender: Entity, dependee: EOptionP[Entity, PointsTo]): Unit = {
        _dependeeToDependers.getOrElseUpdate(dependee.e, mutable.Set.empty).add(depender)
        _dependerToDependees.getOrElseUpdate(depender, mutable.Set.empty).add(dependee.e)
        _dependees(dependee.e) = dependee
        assert(_dependees.contains(dependee.e) && _dependeeToDependers.contains(dependee.e))
    }

    def removeDependee(eps: SomeEPS): Unit = {
        val dependee = eps.e
        assert(_dependees.contains(dependee))
        _dependees.remove(dependee)
        val dependers = _dependeeToDependers(dependee)
        _dependeeToDependers.remove(dependee)
        for (depender ← dependers) {
            val dependees = _dependerToDependees(depender)
            dependees.remove(dependee)
            if (dependees.isEmpty)
                _dependerToDependees.remove(depender)
        }
    }

    def updatePointsToDependee(eps: EOptionP[Entity, PointsTo]): Unit = {
        _dependees(eps.e) = eps
    }

    def setOrUpdatePointsToSet(e: Entity, p2s: UIDSet[ObjectType]): Unit = {
        if (_pointsToSets.contains(e)) {
            val newPointsToSet = _pointsToSets(e) ++ p2s
            _pointsToSets(e) = newPointsToSet
        } else {
            _pointsToSets(e) = p2s
        }
    }

    def clearPointsToSet(): Unit = {
        _pointsToSets.clear()
    }

    def dependersOf(dependee: Entity): Traversable[Entity] = _dependeeToDependers(dependee)

    def hasDependees(potentialDepender: Entity): Boolean =
        _dependerToDependees.contains(potentialDepender)
}

object PointsToState {
    def apply(
        method:      DefinedMethod,
        tacDependee: EOptionP[Method, TACAI]
    ): PointsToState = {
        new PointsToState(method, Some(tacDependee))
    }
}

class AndersenStylePointsToAnalysis private[analyses] (
        final val project: SomeProject
) extends FPCFAnalysis {

    private implicit val declaredMethods: DeclaredMethods = p.get(DeclaredMethodsKey)
    private val formalParameters: VirtualFormalParameters = p.get(VirtualFormalParametersKey)
    private val definitionSites: DefinitionSites = p.get(DefinitionSitesKey)

    def analyze(declaredMethod: DeclaredMethod): PropertyComputationResult = {
        (propertyStore(declaredMethod, CallersProperty.key): @unchecked) match {
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

        val tacEP = propertyStore(method, TACAI.key)
        implicit val state: PointsToState = PointsToState(declaredMethod.asDefinedMethod, tacEP)

        if (tacEP.hasUBP && tacEP.ub.tac.isDefined) {
            processMethod
        } else {
            InterimPartialResult(Some(tacEP), c(state))
        }
    }

    private[this] def c(state: PointsToState)(eps: SomeEPS): ProperPropertyComputationResult = {
        if (state.method.name == "id")
            println()
        eps match {
            case UBP(tacai: TACAI) if tacai.tac.isDefined ⇒
                state.updateTACDependee(eps.asInstanceOf[EPS[Method, TACAI]])
                processMethod(state)

            case UBP(_: TACAI) ⇒
                InterimPartialResult(Some(eps), c(state))

            case UBPS(pointsTo: PointsTo, isFinal) ⇒
                for (depender ← state.dependersOf(eps.e)) {
                    state.setOrUpdatePointsToSet(depender, pointsTo.types)
                }

                if (isFinal)
                    state.removeDependee(eps)
                else
                    state.updatePointsToDependee(eps.asInstanceOf[EPS[Entity, PointsTo]])

                returnResult(state)

            case UBP(callees: Callees) ⇒
                // todo get new calls for all pcs
                //returnResult(state)
                state.updateCalleesDependee(eps.asInstanceOf[EPS[DeclaredMethod, Callees]])
                processMethod(state)
        }
    }

    @inline private[this] def toEntity(
        defSite: Int
    )(implicit state: PointsToState): Entity = {
        if (defSite < 0) {
            formalParameters.apply(state.method)(-1 - defSite)
        } else {
            definitionSites(state.method.definedMethod, state.tac.stmts(defSite).pc)
        }
    }

    @inline private[this] def handleEOptP(
        depender: Entity, dependeeDefSite: Int
    )(implicit state: PointsToState): UIDSet[ObjectType] = {
        if (ai.isImplicitOrExternalException(dependeeDefSite)) {
            // todo -  we need to get the actual exception type here
            UIDSet(ObjectType.Exception)
        } else {
            handleEOptP(depender, toEntity(dependeeDefSite))
        }
    }

    // todo: rename
    // IMPROVE: use local information, if possible
    // todo: we should only retrieve a dependency once per analysis
    @inline private[this] def handleEOptP(
        depender: Entity, dependee: Entity
    )(implicit state: PointsToState): UIDSet[ObjectType] = {
        // IMPROVE: do not add a dependency twice
        val pointsToSetEOptP = state._dependees.getOrElse(dependee, ps(dependee, PointsTo.key))
        pointsToSetEOptP match {
            case UBPS(pointsTo, isFinal) ⇒
                if (!isFinal) state.addPointsToDependency(depender, pointsToSetEOptP)
                pointsTo.types

            case _: EPK[Entity, PointsTo] ⇒
                state.addPointsToDependency(depender, pointsToSetEOptP)
                UIDSet.empty
        }
    }

    // todo: rename
    @inline private[this] def handleDefSites(e: Entity, defSites: IntTrieSet)(implicit state: PointsToState): Unit = {
        var pointsToSet = UIDSet.empty[ObjectType]
        for (defSite ← defSites) {
            pointsToSet ++=
                handleEOptP(e, defSite)

        }

        state.setOrUpdatePointsToSet(e, pointsToSet)
    }

    @inline private[this] def isArrayType(value: DUVar[ValueInformation]): Boolean = {
        value.value.isReferenceValue && {
            val lub = value.value.asReferenceValue.leastUpperType
            lub.isDefined && lub.get.isArrayType
        }
    }

    @inline private[this] def isArrayOfObjectType(value: DUVar[ValueInformation]): Boolean = {
        value.value.isReferenceValue && {
            val lub = value.value.asReferenceValue.leastUpperType
            lub.isDefined && lub.get.isArrayType && lub.get.asArrayType.elementType.isObjectType
        }
    }

    @inline private[this] def getArrayBaseObjectType(value: DUVar[ValueInformation]): ObjectType = {
        value.value.asReferenceValue.leastUpperType.get.asArrayType.elementType.asObjectType
    }

    private[this] def processMethod(implicit state: PointsToState): ProperPropertyComputationResult = {
        val tac = state.tac
        val method = state.method.definedMethod

        for (stmt ← tac.stmts) stmt match {
            case Assignment(pc, _, New(_, t)) ⇒
                val defSite = definitionSites(method, pc)
                state.setOrUpdatePointsToSet(defSite, UIDSet(t))

            case Assignment(pc, _, NewArray(_, _, _)) ⇒
                val defSite = definitionSites(method, pc)

                // todo: use correct type
                state.setOrUpdatePointsToSet(defSite, UIDSet(ObjectType.Object))

            // that case should not happen
            case Assignment(pc, targetVar, UVar(_, defSites)) if targetVar.value.isReferenceValue ⇒
                val defSiteObject = definitionSites(method, pc)
                handleDefSites(defSiteObject, defSites)

            case Assignment(pc, targetVar, GetField(_, declaringClass, name, fieldType, _)) if targetVar.value.isReferenceValue ⇒
                val defSiteObject = definitionSites(method, pc)
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    state.setOrUpdatePointsToSet(defSiteObject, handleEOptP(defSiteObject, fieldOpt.get))
                } else {
                    // todo: handle the case that the field does not exists
                }

            case Assignment(pc, targetVar, GetStatic(_, declaringClass, name, fieldType)) if targetVar.value.isReferenceValue ⇒
                val defSiteObject = definitionSites(method, pc)
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    state.setOrUpdatePointsToSet(defSiteObject, handleEOptP(defSiteObject, fieldOpt.get))
                } else {
                    // todo: handle the case that the field does not exists
                }

            case Assignment(pc, _, ArrayLoad(_, _, arrayRef)) if isArrayOfObjectType(arrayRef.asVar) ⇒
                val defSiteObject = definitionSites(method, pc)
                val arrayBaseType = getArrayBaseObjectType(arrayRef.asVar)
                state.setOrUpdatePointsToSet(defSiteObject, handleEOptP(defSiteObject, arrayBaseType))

            case Assignment(pc, targetVar, call: FunctionCall[DUVar[ValueInformation]]) ⇒
                val targets = state.callees(ps).callees(pc)
                val defSiteObject = definitionSites(method, pc)

                if (targetVar.value.isReferenceValue) {
                    var pointsToSet = UIDSet.empty[ObjectType]
                    for (target ← targets) {
                        pointsToSet ++= handleEOptP(defSiteObject, target)
                    }

                    state.setOrUpdatePointsToSet(defSiteObject, pointsToSet)
                }

                for (target ← state.callees(ps).callees(pc)) {
                    val fps = formalParameters(target)

                    if (fps != null) {
                        val receiverOpt = call.receiverOption
                        // handle receiver for non static methods
                        if (receiverOpt.isDefined) {
                            val fp = fps(0)
                            handleDefSites(fp, receiverOpt.get.asVar.definedBy)
                        }

                        // handle params
                        for (i ← 0 until target.descriptor.parametersCount) {
                            val fp = fps(i + 1)
                            handleDefSites(fp, call.params(i).asVar.definedBy)
                        }
                    } else {
                        // todo handle library methods, where no formal params exists
                    }
                }

            case Assignment(pc, targetVar, _: Const) if targetVar.value.isReferenceValue ⇒
                val defSite = definitionSites(method, pc)

                state.setOrUpdatePointsToSet(defSite, UIDSet.empty)

            case Assignment(_, targetVar, _) if (targetVar.value.isReferenceValue && !isArrayType(targetVar)) || isArrayOfObjectType(targetVar) ⇒
                throw new IllegalArgumentException(s"unexpected assignment: $stmt")

            case call: Call[_] ⇒
                val targets = state.callees(ps).callees(call.pc)
                for (target ← targets) {
                    val fps = formalParameters(target)

                    if (fps != null) {
                        val receiverOpt = call.receiverOption
                        // handle receiver for non static methods
                        if (receiverOpt.isDefined) {
                            val fp = fps(0)
                            handleDefSites(fp, receiverOpt.get.asVar.asInstanceOf[DUVar[ValueInformation]].definedBy)
                        }

                        // handle params
                        for (i ← 0 until target.descriptor.parametersCount) {
                            val fp = fps(i + 1)
                            handleDefSites(fp, call.params(i).asVar.asInstanceOf[DUVar[ValueInformation]].definedBy)
                        }
                    } else {
                        // todo handle library methods, where no formal params exists
                    }
                }

            case ArrayStore(_, arrayRef, _, UVar(_, defSites)) if isArrayOfObjectType(arrayRef.asVar) ⇒
                val arrayBaseType = getArrayBaseObjectType(arrayRef.asVar)
                handleDefSites(arrayBaseType, defSites)

            case PutField(_, declaringClass, name, fieldType, _, UVar(_, defSites)) if fieldType.isObjectType ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined)
                    handleDefSites(fieldOpt.get, defSites)
                else {
                    // todo: field does not exists
                }

            case PutStatic(_, declaringClass, name, fieldType, UVar(_, defSites)) if fieldType.isObjectType ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined)
                    handleDefSites(fieldOpt.get, defSites)
                else {
                    // todo: field does not exists
                }

            case ReturnValue(_, value @ UVar(_, defSites)) if value.value.isReferenceValue ⇒
                handleDefSites(state.method, defSites)

            case _ ⇒
        }

        returnResult
    }

    @inline private[this] def returnResult(implicit state: PointsToState): ProperPropertyComputationResult = {
        val results = ArrayBuffer.empty[ProperPropertyComputationResult]

        if (state.hasOpenDependees) results += InterimPartialResult(state.dependees, c(state))

        for ((e, pointsToSet) ← state._pointsToSets) {
            // todo: ensure isFinal is set correctly
            //val isFinal = e.isInstanceOf[DefinitionSite] && !state.hasDependees(e)
            results += PartialResult[Entity, PointsTo](e, PointsTo.key, {
                //case _: EPK[Entity, PointsTo] if isFinal ⇒
                //    Some(FinalEP(e, PointsTo(pointsToSet)))

                case _: EPK[Entity, PointsTo] ⇒
                    Some(InterimEUBP(e, PointsTo(pointsToSet)))

                case UBP(ub) ⇒
                    // IMPROVE: only process new Types
                    val newPointsTo = ub.updated(pointsToSet)
                    if (newPointsTo.numElements != ub.numElements)
                        //if (isFinal)
                        //    Some(FinalEP(e, newPointsTo))
                        //else
                        Some(InterimEUBP(e, newPointsTo))
                    else
                        None
            })
        }

        // todo: ensure this is correct
        state.clearPointsToSet()

        Results(results)
    }
}

object AndersenStylePointsToAnalysisScheduler extends FPCFTriggeredAnalysisScheduler {
    override type InitializationData = Null

    override def uses: Set[PropertyBounds] = PropertyBounds.ubs(
        CallersProperty,
        Callees,
        PointsTo,
        TACAI
    )

    override def derivesCollaboratively: Set[PropertyBounds] = PropertyBounds.ubs(PointsTo)

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def init(p: SomeProject, ps: PropertyStore): Null = {
        null
    }

    override def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): AndersenStylePointsToAnalysis = {
        val analysis = new AndersenStylePointsToAnalysis(p)
        // register the analysis for initial values for callers (i.e. methods becoming reachable)
        ps.registerTriggeredComputation(CallersProperty.key, analysis.analyze)
        analysis
    }

    override def afterPhaseScheduling(ps: PropertyStore, analysis: FPCFAnalysis): Unit = {}

    override def afterPhaseCompletion(
        p:        SomeProject,
        ps:       PropertyStore,
        analysis: FPCFAnalysis
    ): Unit = {}
}
