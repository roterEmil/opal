/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.immutability.reference

import org.opalj.RelationalOperators.EQ
import org.opalj.RelationalOperators.NE
import org.opalj.ai.isImmediateVMException
import org.opalj.ai.pcOfImmediateVMException
import org.opalj.ai.pcOfMethodExternalException
import org.opalj.br.ComputationalTypeFloat
import org.opalj.br.ComputationalTypeInt
import org.opalj.br.Method
import org.opalj.br.PC
import org.opalj.br.PCs
import org.opalj.br.cfg.BasicBlock
import org.opalj.br.cfg.CFG
import org.opalj.br.cfg.CFGNode
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.properties.ImmutableReference
import org.opalj.br.fpcf.properties.LazyInitializedNotThreadSafeButDeterministicReference
import org.opalj.br.fpcf.properties.LazyInitializedNotThreadSafeReference
import org.opalj.br.fpcf.properties.LazyInitializedThreadSafeReference
import org.opalj.br.fpcf.properties.MutableReference
import org.opalj.br.fpcf.properties.Purity
import org.opalj.br.fpcf.properties.ReferenceImmutability
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.tac.SelfReferenceParameter
import org.opalj.tac.Assignment
import org.opalj.tac.CaughtException
import org.opalj.tac.ClassConst
import org.opalj.tac.DVar
import org.opalj.tac.Expr
import org.opalj.tac.FieldWriteAccessStmt
import org.opalj.tac.GetField
import org.opalj.tac.GetStatic
import org.opalj.tac.If
import org.opalj.tac.MonitorEnter
import org.opalj.tac.MonitorExit
import org.opalj.tac.NonVirtualFunctionCall
import org.opalj.tac.ReturnValue
import org.opalj.tac.StaticFunctionCall
import org.opalj.tac.Stmt
import org.opalj.tac.TACMethodParameter
import org.opalj.tac.TACStmts
import org.opalj.tac.TACode
import org.opalj.tac.UVar
import org.opalj.tac.VirtualFunctionCall
import org.opalj.tac.Throw
import org.opalj.br.ObjectType
import scala.annotation.switch

trait AbstractReferenceImmutabilityAnalysisLazyInitialization
    extends AbstractReferenceImmutabilityAnalysis
    with FPCFAnalysis {

    /**
     * handles the lazy initialization determinations for the method
     * methodUpdatesField
     * Returns Some(true) if we have no thread safe or deterministic lazy initialization
     *
     */
    def handleLazyInitialization(
        writeIndex:   Int,
        defaultValue: Any,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]],
        pcToIndex:    Array[Int],
        tacCai:       TACode[TACMethodParameter, V]
    )(implicit state: State): Option[Boolean] = {
        var result: Option[Boolean] = None
        val dcl: ReferenceImmutability =
            isThreadSafeLazyInitialization(writeIndex, defaultValue, method, code, cfg, pcToIndex, tacCai)
        dcl match {
            case ImmutableReference |
                LazyInitializedThreadSafeReference |
                LazyInitializedNotThreadSafeButDeterministicReference ⇒
                state.referenceImmutability = dcl
            case MutableReference | LazyInitializedNotThreadSafeReference ⇒
                /*val lazyInitialization = isLazyInitialization(writeIndex, defaultValue, method, code, cfg, pcToIndex)
                println("result lazyInitialization: "+lazyInitialization)
                if (lazyInitialization)
                    state.referenceImmutability = LazyInitializedNotThreadSafeButDeterministicReference
                else if (dcl == MutableReference) { */
                /*if (!lazyInitialization) {
                        result = Some(true)
                    } else
                        state.referenceImmutability = LazyInitializedNotThreadSafeReference*/
                state.referenceImmutability = dcl
                if (dcl == MutableReference)
                    result = Some(true)
            //} else state.referenceImmutability = LazyInitializedNotThreadSafeReference
        }
        result
    }

    /**
     * Checks whether a field write may be a lazy initialization.
     *
     * We only consider simple cases of lazy initialization where the single field write is guarded
     * so it is executed only if the field still has its default value and where the written value
     * is guaranteed to be the same even if the write is executed multiple times (may happen because
     * of the initializing method being executed more than once on concurrent threads).
     *
     * Also, all field reads must be used only for a guard or their uses have to be guarded
     * themselves.
     */
    def isLazyInitialization(
        writeIndex:   Int,
        defaultValue: Any,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]],
        pcToIndex:    Array[Int],
        tacCode:      TACode[TACMethodParameter, V]
    )(implicit state: State): Boolean = {
        val write = code(writeIndex).asFieldWriteAccessStmt

        if (state.field.fieldType.computationalType != ComputationalTypeInt &&
            state.field.fieldType.computationalType != ComputationalTypeFloat) {
            // Only handle lazy initialization of ints and floats as they are guaranteed to be
            // written atomically
            return false;
        }

        //TODO reasoning if there is another way to do this
        val writes = fieldAccessInformation.writeAccesses(state.field)

        if (writes.exists(x ⇒ ((x._1 eq method) && x._2.size > 1))) { //filter(mAndPCs ⇒ (mAndPCs._1 eq method))
            return false; // more than one write in the method
        }
        val reads = fieldAccessInformation.readAccesses(state.field)
        if (reads.exists(mAndPCs ⇒ (mAndPCs._1 ne method) && !mAndPCs._1.isInitializer)) {
            return false; // Reads outside the (single) lazy initialization method
        }

        // There must be a guarding if-Statement
        // The guardIndex is the index of the if-Statement, the guardedIndex is the index of the
        // first statement that is executed after the if-Statement if the field's value was not the
        // default value
        val (guardIndex, guardedIndex, readIndex) = {
            val findGuardResult = findGuard(method, writeIndex, defaultValue, code, cfg, tacCode)
            if (findGuardResult.isDefined)
                (findGuardResult.get._1, findGuardResult.get._2, findGuardResult.get._3)
            else return false;
        }

        // Detect only simple patterns where the lazily initialized value is returned immediately
        if (!checkImmediateReturn(write, writeIndex, readIndex, code, tacCode)) {
            return false;
        };

        // The value written must be computed deterministically and the writes guarded correctly
        if (!checkWrites(write, writeIndex, guardIndex, guardedIndex, method, code, cfg)) {
            return false;
        };

        // Field reads (except for the guard) may only be executed if the field's value is not the
        // default value
        if (!checkReads(reads, readIndex, guardedIndex, writeIndex, cfg, pcToIndex)) {
            return false
        }
        true
    }

    /**
     * Determines if a given field is thread safe lazy initialized.
     * E.g. in a synchronized method with a nonnull-check.
     */

    def isThreadSafeLazyInitialization(
        writeIndex:   Int,
        defaultValue: Any,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]],
        pcToIndex:    Array[Int],
        tacCode:      TACode[TACMethodParameter, V]
    )(implicit state: State): ReferenceImmutability = {
        //var result: ReferenceImmutability = LazyInitializedThreadSafeReference
        val write = code(writeIndex).asFieldWriteAccessStmt
        val writeBB = cfg.bb(writeIndex).asBasicBlock
        val domTree = cfg.dominatorTree
        val resultCaughtsAndThrows = findCaughtsThrowsAndResults(tacCode, cfg)

        def noInterferingExceptions: Boolean = {
            //resultCaughtsAndThrows._1.size == resultCaughtsAndThrows._2.size &&
            resultCaughtsAndThrows._1.forall(bbCatch ⇒
                resultCaughtsAndThrows._2.exists(bbThrow ⇒
                    ((domTree.strictlyDominates(bbCatch._4.nodeId, bbThrow._3.nodeId) || //domination
                        (bbCatch._4 == bbThrow._3 && bbCatch._1 < bbThrow._1)) && // or equal and successor
                        bbThrow._2 == bbCatch._3)))
        }

        val (guardIndex, guardedIndex, readIndex, afterGuardRecognizedTheDefaultValueIndex) = {
            val findGuardResult: (Option[(Int, Int, Int, CFGNode, Int)]) =
                findGuard(method, writeIndex, defaultValue, code, cfg, tacCode)
            if (findGuardResult.isDefined)
                (
                    findGuardResult.get._1,
                    findGuardResult.get._2,
                    findGuardResult.get._3,
                    findGuardResult.get._5
                )
            else {
                println("mut1")
                return MutableReference;
            }
        }
        val guardedBB = cfg.bb(afterGuardRecognizedTheDefaultValueIndex)

        val reads = fieldAccessInformation.readAccesses(state.field)
        if (reads.exists(mAndPCs ⇒ (mAndPCs._1 ne method) && !mAndPCs._1.isInitializer)) {
            println("mut2")
            return MutableReference;
        }
        val writes = fieldAccessInformation.writeAccesses(state.field)
        if (writes.exists(x ⇒ ((x._1 eq method) && x._2.size > 1))) {
            println("mut3")
            return MutableReference;
        }
        if (method.returnType == state.field.fieldType &&
            !checkThatTheValueOfTheFieldIsReturned(write, writeIndex, readIndex, code, tacCode)) {
            println("mut4")
            return MutableReference;
        }
        //when the method is synchronized the monitor has not to be searched
        if (method.isSynchronized) {
            if (domTree.strictlyDominates(guardedBB.nodeId, writeBB.nodeId) ||
                (guardedBB == writeBB && afterGuardRecognizedTheDefaultValueIndex < writeIndex)) {

                if (noInterferingExceptions) {
                    LazyInitializedThreadSafeReference // result //DCL
                } else {
                    println("mut5")
                    MutableReference
                }
            } else {
                println("mut6")
                MutableReference
            }
        } else {
            val monitorResult: ((Option[Int], Option[Int]), (Option[CFGNode], Option[CFGNode])) =
                findMonitorCaughtsAndThrowsAndResult(writeIndex, defaultValue, code, cfg, tacCode)

            if ((monitorResult._1._1.isDefined && monitorResult._1._2.isDefined && monitorResult._2._1.isDefined)
                &&
                (
                    (domTree.strictlyDominates(monitorResult._2._1.get.nodeId, guardedBB.nodeId) ||
                        (monitorResult._2._1.get == guardedBB && monitorResult._1._1.get < afterGuardRecognizedTheDefaultValueIndex)) && //writeIndex)) && //monitor enter dominates guard1
                        ((domTree.strictlyDominates(guardedBB.nodeId, writeBB.nodeId))
                            || guardedBB == writeBB && afterGuardRecognizedTheDefaultValueIndex < writeIndex) //&& //true case dominates Write
                )) {
                if (noInterferingExceptions)
                    LazyInitializedThreadSafeReference // result //DCL
                else {
                    println("mut7")
                    MutableReference
                }
            } else {
                /*
                println("xxx0")
                println("defined by: "+write.value.asVar.definedBy.head)
                println("def by inner: " + tacCode.stmts(write.value.asVar.definedBy.filter(n⇒ n>0).head))
                println("write: "+write)
                var f: Expr[V] = null
                if (write.isPutField)
                    f = write.asPutField.value
                if (write.isPutStatic) {
                    f = write.asPutStatic.value

                }
                println("field: "+f)

              if(write.value.asVar.definedBy.size>1)
                  tacCode.stmts(write.value.asVar.definedBy.filter(n⇒ n>0).head).asAssignment.expr
              */
                /* val propertyStoreResultFieldImmutability = propertyStore(f, FieldImmutability.key)
println("propertystore result field imm: "+propertyStoreResultFieldImmutability)
state.fieldDependees = state.ldDependees.filter(_.e ne propertyStoreResultFieldImmutability.e)
println("propertystore result: "+propertyStoreResultFieldImmutability) */
                /*val fieldIsDeepImmutable =
    propertyStoreResultFieldImmutability match {
        case FinalP(DeepImmutableField) ⇒ true
        case FinalP(_)                  ⇒ false
        case ep ⇒
            state.fieldDependees = state.fieldDependees + ep
            true
    } */
                /*if(write.value.asVar.definedBy.head>0) {
   val f = write.value.asVar.definedBy.head
}*/ /**/
                /*println(
                    s"""
                     | (domTree.strictlyDominates(guardedBB.nodeId, writeBB.nodeId) : ${(domTree.strictlyDominates(guardedBB.nodeId, writeBB.nodeId))}
                     | (guardedBB == writeBB && afterGuardRecognizedTheDefaultValueIndex < writeIndex): ${(guardedBB == writeBB && afterGuardRecognizedTheDefaultValueIndex < writeIndex)}
                     | write.value.asVar.definedBy.size > 0: ${write.value.asVar.definedBy.size > 0}
                     | checkWriteIsDeterministic(code(write.value.asVar.definedBy.iterator.filter(n ⇒ n >= 0).toList.head).asAssignment, method, code): ${checkWriteIsDeterministic(code(write.value.asVar.definedBy.iterator.filter(n ⇒ n >= 0).toList.head).asAssignment, method, code)}
                     | write.value.asVar.definedBy: ${write.value.asVar.definedBy.map(code(_)).mkString(", \n")}
                     |""".stripMargin
                ) */

                if ((domTree.strictlyDominates(guardedBB.nodeId, writeBB.nodeId) ||
                    (guardedBB == writeBB && afterGuardRecognizedTheDefaultValueIndex < writeIndex)) &&
                    write.value.asVar.definedBy.size >= 0 &&
                    (( //IsDeepImmutable //state.field.isFinal ||write.value.asVar.definedBy.head > -1
                        !write.value.asVar.definedBy.iterator.filter(n ⇒ n >= 0).toList.isEmpty &&
                        checkWriteIsDeterministic(code(write.value.asVar.definedBy.iterator.filter(n ⇒ n >= 0).toList.head).asAssignment, method, code)
                    ))) {
                    //LazyInitializedNotThreadSafeReference
                    println("-----------------------------------------------------------------------------Aui")
                    println(
                        s"""
                           | noInterferingExceptions: $noInterferingExceptions
                           | comp type: ${state.field.fieldType.computationalType}
                           |""".stripMargin
                    )
                    if (noInterferingExceptions) {
                        if (state.field.fieldType.computationalType != ComputationalTypeInt &&
                            state.field.fieldType.computationalType != ComputationalTypeFloat) {
                            LazyInitializedNotThreadSafeReference
                        } else
                            LazyInitializedNotThreadSafeButDeterministicReference
                    } else {
                        println("mut8")
                        MutableReference
                    }
                    //checkWriteIsGuarded2(writeIndex, guardIndex, guardedIndex, method, code, cfg) //, tacCode.stmts)

                } else {
                    println("mut9")
                    MutableReference
                }
            }
        }
    }

    def findCaughtsThrowsAndResults(
        tacCode: TACode[TACMethodParameter, V],
        cfg:     CFG[Stmt[V], TACStmts[V]]
    ): (List[(Int, ObjectType, IntTrieSet, CFGNode)], List[(Int, IntTrieSet, CFGNode)], Option[CFGNode]) = {
        var exceptions: List[(Int, ObjectType, IntTrieSet, CFGNode)] = List.empty
        var throwers: List[(Int, IntTrieSet, CFGNode)] = List.empty
        var resultNode: Option[CFGNode] = None
        for (stmt ← tacCode.stmts) {
            if (!stmt.isNop) {
                val curBB = cfg.bb(tacCode.pcToIndex(stmt.pc))
                (stmt.astID: @switch) match {
                    case CaughtException.ASTID ⇒
                        val ce = stmt.asCaughtException
                        val et =
                            if (ce.exceptionType.isDefined) {
                                val intermdT = ce.exceptionType.get
                                if (intermdT.isObjectType)
                                    intermdT.asObjectType
                                else
                                    ObjectType.Exception
                            } else
                                ObjectType.Exception
                        exceptions = (ce.pc, et, ce.origins, curBB) :: exceptions

                    case Throw.ASTID ⇒
                        val t = stmt.asThrow
                        val ds = if (t.exception.isVar) {
                            val v = t.exception.asVar
                            v.definedBy
                        } else
                            IntTrieSet.empty
                        throwers = (t.pc.toInt, ds, curBB) :: throwers
                    case ReturnValue.ASTID ⇒
                        //case ReturnValue(pc, expr) ⇒
                        resultNode = Some(curBB)
                    case _ ⇒
                }
            }
        }
        (exceptions, throwers, resultNode)
    }

    def findMonitorCaughtsAndThrowsAndResult(
        fieldWrite:   Int,
        defaultValue: Any,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]],
        tacCode:      TACode[TACMethodParameter, V]
    )(implicit state: State): ((Option[Int], Option[Int]), (Option[CFGNode], Option[CFGNode])) = {

        var result: (Option[Int], Option[Int]) = (None, None)
        var dclEnterBBs: List[CFGNode] = List.empty
        var dclExitBBs: List[CFGNode] = List.empty
        val startBB = cfg.bb(fieldWrite)
        var MonitorExitqueuedBBs: Set[CFGNode] = startBB.successors
        var worklistMonitorExit = getSuccessors(startBB, Set.empty).toList

        def checkMonitor(pc: PC, v: V, curBB: CFGNode)(
            implicit
            state: State
        ): Boolean = {
            v.definedBy.iterator
                .filter(i ⇒ {
                    if (i > 0) {
                        val stmt = tacCode.stmts(i)
                        stmt match {
                            case Assignment(pc1, DVar(useSites, value), cc @ ClassConst(_, constant)) ⇒ {
                                state.field.classFile.thisType == cc.value || state.field.fieldType == cc.value
                            }
                            case Assignment(
                                pc1,
                                DVar(value1, defSites1),
                                GetField(pc2, t, name, classType, UVar(value2, defSites2))
                                ) ⇒
                                classType ==
                                    state.field.classFile.thisType
                            //&& name == state.field.name
                            case _ ⇒ false
                        }
                    } else // (i <= -1)
                        true
                })
                .size == v.definedBy.size
        }

        var monitorEnterQueuedBBs: Set[CFGNode] = startBB.predecessors
        var worklistMonitorEnter = getPredecessors(startBB, Set.empty)

        //find monitorenter
        while (!worklistMonitorEnter.isEmpty) {
            val curBB = worklistMonitorEnter.head

            worklistMonitorEnter = worklistMonitorEnter.tail
            val startPC = curBB.startPC
            val endPC = curBB.endPC
            var flag = true
            for (i ← startPC to endPC) {
                (code(i).astID: @switch) match {
                    case MonitorEnter.ASTID ⇒
                        val me = code(i).asMonitorEnter
                        if (checkMonitor(me.pc, me.objRef.asVar, curBB)) {
                            result = (Some(tacCode.pcToIndex(me.pc)), (result._2))
                            dclEnterBBs = curBB :: dclEnterBBs
                            flag = false
                        }
                    case _ ⇒
                }
            }
            if (flag) {
                val predecessor = getPredecessors(curBB, monitorEnterQueuedBBs)
                worklistMonitorEnter ++= predecessor
                monitorEnterQueuedBBs ++= predecessor
            }
        }

        //find monitorexit
        while (!worklistMonitorExit.isEmpty) {
            val curBB = worklistMonitorExit.head
            worklistMonitorExit = worklistMonitorExit.tail
            val endPC = curBB.endPC

            val cfStmt = code(endPC)
            (cfStmt.astID: @switch) match {
                case MonitorExit.ASTID ⇒
                    val mex = cfStmt.asMonitorExit
                    if (checkMonitor(mex.pc, mex.objRef.asVar, curBB)) {
                        result = ((result._1), Some(tacCode.pcToIndex(mex.pc)))
                        dclExitBBs = curBB :: dclExitBBs
                    }
                case _ ⇒
                    val successors = getSuccessors(curBB, MonitorExitqueuedBBs)
                    worklistMonitorExit ++= successors
                    MonitorExitqueuedBBs ++= successors
            }
        }

        val bbsEnter = {
            if (dclEnterBBs.size >= 1)
                Some(dclEnterBBs.head)
            else None
        }
        val bbsExit = {
            if (dclExitBBs.size >= 1)
                Some(dclExitBBs.head)
            else None
        }
        (result, (bbsEnter, bbsExit))
    }

    /**
     * Finds the index of the guarding if-Statement for a lazy initialization, the index of the
     * first statement executed if the field does not have its default value and the index of the
     * field read used for the guard.
     */
    // var savedGuards: mutable.HashMap[(Int, Method), ((Option[(Int, Int, Int, CFGNode, Int)]))] =
    //     new mutable.HashMap[(Int, Method), ((Option[(Int, Int, Int, CFGNode, Int)]))]()

    def findGuard(
        method:       Method,
        fieldWrite:   Int,
        defaultValue: Any,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]],
        tacCode:      TACode[TACMethodParameter, V]
    )(implicit state: State): (Option[(Int, Int, Int, CFGNode, Int)]) = {
        val startBB = cfg.bb(fieldWrite).asBasicBlock

        var enqueuedBBs: Set[CFGNode] = startBB.predecessors
        var worklist: List[BasicBlock] = getPredecessors(startBB, Set.empty)

        var result: Option[(Int, Int, CFGNode, Int)] = None

        while (worklist.nonEmpty) {
            val curBB = worklist.head
            worklist = worklist.tail

            val startPC = curBB.startPC
            val endPC = curBB.endPC

            val cfStmt = code(endPC)
            (cfStmt.astID: @switch) match {
                case If.ASTID ⇒
                    val ifStmt = cfStmt.asIf
                    //ifStmt.condition match {
                    if (ifStmt.condition.equals(EQ) && curBB != startBB && isGuard(
                        ifStmt,
                        defaultValue,
                        code,
                        tacCode
                    )) {
                        //case EQ
                        //if =>
                        if (result.isDefined) {
                            if (result.get._1 != endPC || result.get._2 != endPC + 1)
                                return None;
                        } else {
                            result = Some((endPC, endPC + 1, curBB, ifStmt.targetStmt))
                        }
                    } else if (ifStmt.condition.equals(NE) && curBB != startBB && isGuard(
                        ifStmt,
                        defaultValue,
                        code,
                        tacCode
                    )) {
                        //case NE
                        //if =>
                        if (result.isDefined) {
                            if (result.get._1 != endPC || result.get._2 != ifStmt.targetStmt)
                                return None
                        } else {
                            result = Some((endPC, ifStmt.targetStmt, curBB, endPC + 1))
                        }
                    } else {
                        // Otherwise, we have to ensure that a guard is present for all predecessors
                        //case _ =>
                        if (startPC == 0) return None;
                        val predecessors = getPredecessors(curBB, enqueuedBBs)
                        worklist ++= predecessors
                        enqueuedBBs ++= predecessors
                    }
                //}

                // Otherwise, we have to ensure that a guard is present for all predecessors
                case _ ⇒
                    if (startPC == 0) return None;

                    val predecessors = getPredecessors(curBB, enqueuedBBs)
                    worklist ++= predecessors
                    enqueuedBBs ++= predecessors
            }
        }

        val finalResult: Option[(Int, Int, Int, CFGNode, Int)] =
            if (result.isDefined) {
                // The field read that defines the value checked by the guard must be used only for the
                // guard or directly if the field's value was not the default value
                val ifStmt = code(result.get._1).asIf
                val expr = if (ifStmt.leftExpr.isConst) ifStmt.rightExpr else ifStmt.leftExpr
                val definitions = expr.asVar.definedBy
                val fieldReadUses = code(definitions.head).asAssignment.targetVar.usedBy
                val fieldReadUsedCorrectly = fieldReadUses forall { use ⇒
                    use == result.get._1 || use == result.get._2
                }
                if (definitions.size == 1 && fieldReadUsedCorrectly)
                    Some((result.get._1, result.get._2, definitions.head, result.get._3, result.get._4)); // Found proper guard
                else None
            } else None
        finalResult

    }

    /**
     * Checks if the field write is only executed if the field's value was still the default value.
     * Also, no exceptions may be thrown between the guarding if-Statement of a lazy initialization
     * and the field write.
     */
    def checkWriteIsGuarded(
        writeIndex:   Int,
        guardIndex:   Int,
        guardedIndex: Int,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]]
    )(implicit state: State): Boolean = {
        val startBB = cfg.bb(writeIndex).asBasicBlock
        var enqueuedBBs: Set[CFGNode] = Set(startBB)
        var worklist: List[BasicBlock] = List(startBB.asBasicBlock)

        val abnormalReturnNode = cfg.abnormalReturnNode

        val caughtExceptions = code.iterator
            .filter { stmt ⇒
                stmt.astID == CaughtException.ASTID

            }
            .flatMap { exception ⇒
                exception.asCaughtException.origins.iterator.map { origin: Int ⇒
                    if (isImmediateVMException(origin)) {
                        pcOfImmediateVMException(origin)
                    } else {
                        pcOfMethodExternalException(origin)
                    }
                }
            }
            .toSet
        while (worklist.nonEmpty) {
            val curBB = worklist.head
            worklist = worklist.tail
            val startPC = curBB.startPC
            val endPC = curBB.endPC
            if (startPC == 0 || startPC == guardedIndex)
                return false; // Reached method start or wrong branch of guarding if-Statement
            // Exception thrown between guard and write, which is ok for deterministic methods,
            // but may be a problem otherwise as the initialization is not guaranteed to happen
            // (or never happen).
            if ((curBB ne startBB) && abnormalReturnNode.predecessors.contains(curBB)) {
                if (!lazyInitializerIsDeterministic(method, code)) {
                    return false;
                }
            }
            // Exception thrown between guard and write (caught somewhere, but we don't care)
            if ((curBB ne startBB) & caughtExceptions.contains(endPC)) {
                if (!lazyInitializerIsDeterministic(method, code)) {
                    return false;
                }

            }
            // Check all predecessors except for the one that contains the guarding if-Statement
            val predecessors =
                getPredecessors(curBB, enqueuedBBs).iterator.filterNot(_.endPC == guardIndex).toList
            worklist ++= predecessors
            enqueuedBBs ++= predecessors
        }
        true
    }

    def checkWriteIsGuarded2(
        writeIndex:   Int,
        guardIndex:   Int,
        guardedIndex: Int,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]]
    )(implicit state: State): ReferenceImmutability = {
        val startBB = cfg.bb(writeIndex).asBasicBlock
        var enqueuedBBs: Set[CFGNode] = Set(startBB)
        var worklist: List[BasicBlock] = List(startBB.asBasicBlock)
        val abnormalReturnNode = cfg.abnormalReturnNode

        val caughtExceptions = code.iterator
            .filter { stmt ⇒ stmt.astID == CaughtException.ASTID }
            .flatMap { exception ⇒
                exception.asCaughtException.origins.iterator.map { origin: Int ⇒
                    if (isImmediateVMException(origin)) {
                        pcOfImmediateVMException(origin)
                    } else {
                        pcOfMethodExternalException(origin)
                    }
                }
            }
            .toSet

        /*val caughtExceptions = code.filter { stmt =>
stmt.astID == CaughtException.ASTID
}.toList flatMap {
exception=>
exception.asCaughtException.origins.flatMap { origin =>
    if (isImmediateVMException(origin)) {
        pcOfImmediateVMException(origin)
    } else {
        pcOfMethodExternalException(origin)
    }
}
} */
        while (worklist.nonEmpty) {
            val curBB = worklist.head
            worklist = worklist.tail
            val startPC = curBB.startPC
            val endPC = curBB.endPC
            if (startPC == 0 || startPC == guardedIndex)
                return MutableReference; // Reached method start or wrong branch of guarding if-Statement
            // Exception thrown between guard and write, which is ok for deterministic methods,
            // but may be a problem otherwise as the initialization is not guaranteed to happen
            // (or never happen).
            if ((curBB ne startBB) && abnormalReturnNode.predecessors.contains(curBB)) {
                if (!lazyInitializerIsDeterministic(method, code)) {
                    return MutableReference
                }
            };
            // Exception thrown between guard and write (caught somewhere, but we don't care)
            if ((curBB ne startBB) & caughtExceptions.toSet.contains(endPC)) {
                if (!lazyInitializerIsDeterministic(method, code)) {
                    return MutableReference
                }
            };
            // Check all predecessors except for the one that contains the guarding if-Statement
            val predecessors =
                getPredecessors(curBB, enqueuedBBs).filterNot(_.endPC == guardIndex)
            worklist ++= predecessors
            enqueuedBBs ++= predecessors
        }
        LazyInitializedNotThreadSafeReference
    }

    /**
     * Gets all predecessor BasicBlocks of a CFGNode.
     */
    def getPredecessors(node: CFGNode, visited: Set[CFGNode]): List[BasicBlock] = {
        val result = node.predecessors.iterator flatMap { curNode ⇒
            if (curNode.isBasicBlock)
                if (visited.contains(curNode)) None
                else Some(curNode.asBasicBlock)
            else getPredecessors(curNode, visited)
        }
        result.toList
    }

    /**
     * Gets all successors BasicBlocks of a CFGNode
     */
    def getSuccessors(node: CFGNode, visited: Set[CFGNode]): List[BasicBlock] = {
        val result = node.successors.iterator flatMap ({ currentNode ⇒
            if (currentNode.isBasicBlock)
                if (visited.contains(currentNode)) None
                else Some(currentNode.asBasicBlock)
            else getSuccessors(currentNode, visited)
        })
        result.toList
    }

    /**
     * Checks if the value written to the field is guaranteed to be always the same.
     * This is true if the value is constant or originates from a deterministic call of a method
     * without non-constant parameters. Alternatively, if the initialization method itself is
     * deterministic and has no parameters, the value is also always the same.
     */
    def checkWriteIsDeterministic(
        origin: Assignment[V],
        method: Method,
        code:   Array[Stmt[V]]
    )(implicit state: State): Boolean = {
        import org.opalj.tac.NewArray
        def isConstant(uvar: Expr[V]): Boolean = {
            val defSites = uvar.asVar.definedBy

            def isConstantDef(index: Int) = {
                if (index < 0) false
                else if (code(defSites.head).asAssignment.expr.isConst) true
                else {
                    val expr = code(index).asAssignment.expr
                    expr.isFieldRead && (expr.asFieldRead.resolveField(p) match {
                        case Some(field) ⇒
                            state.field == field || isImmutableReference(propertyStore(field, ReferenceImmutability.key))
                        case _ ⇒ // Unknown field
                            false
                    })
                }
            }

            val result = defSites == SelfReferenceParameter ||
                defSites.size == 1 && isConstantDef(defSites.head)
            result
        }

        val value = origin.expr
        println("value.astID: "+value.astID)
        def isNonConstDeterministic(value: Expr[V]): Boolean = { //val isNonConstDeterministic =
            println("value: "+value)
            value.astID match {
                //case ⇒
                case GetStatic.ASTID | GetField.ASTID ⇒
                    value.asFieldRead.resolveField(p) match {
                        case Some(field) ⇒
                            state.field == field || isImmutableReference(propertyStore(field, ReferenceImmutability.key))
                        case _ ⇒ // Unknown field
                            false
                    }
                case StaticFunctionCall.ASTID | NonVirtualFunctionCall.ASTID | VirtualFunctionCall.ASTID ⇒
                    // If the value originates from a call, that call must be deterministic and may not
                    // have any non constant parameters to guarantee that it is the same on every
                    // invocation. The receiver object must be the 'this' self reference for the same
                    // reason.
                    if (value.asFunctionCall.allParams.exists(!isConstant(_))) {
                        false
                    } else {
                        state.lazyInitInvocation = Some((declaredMethods(method), origin.pc))
                        true
                    }
                case NewArray.ASTID ⇒
                    //TODO after holiday origin.targetVar.usedBy.iterator.filter(i ⇒ code(i).isArrayStore).map(i ⇒ code(i).asArrayStore).
                    //TODO    foreach(arrayStore ⇒ println("arraystore: "+arrayStore))
                    /*println("used bys: ")
                    println("target var: "+origin.targetVar)
                    println("ub: "+origin.targetVar.usedBy)
                    origin.targetVar.usedBy.foreach(i ⇒ println(code(i)))
                    origin.targetVar.usedBy.iterator.filter(i ⇒ code(i).isArrayStore).map(i ⇒ code(i).asArrayStore).
                        foreach(arrayStore ⇒ println(
                            s"""
                         | arraystorevalue: ${arrayStore.value}
                         | isConst: ${isConstant(arrayStore.value)}
                         | isNonConstDeterministic: ${isNonConstDeterministic(arrayStore.value): Boolean}
                         |""".stripMargin
                        ))

                    origin.targetVar.usedBy.iterator.filter(i ⇒ code(i).isArrayStore).map(i ⇒ code(i).asArrayStore).
                        forall(arrayStore ⇒ isNonConstDeterministic(arrayStore.value)) */
                    true //
                case _ if value.isVar ⇒ {
                    val varValue = value.asVar
                    println("value.asVar: def by")
                    varValue.definedBy.forall(i ⇒

                        i >= 0 && code(i).isAssignment && isNonConstDeterministic(code(i).asAssignment.expr))
                    // println(code(i)))
                    //varValue.definedBy.forall(i ⇒isNonConstDeterministic(code(i)))
                    //false
                }
                /* case Assignment.ASTID ⇒ {

                }*/
                case _ if value.isNew ⇒ {
                    println(origin.asAssignment.targetVar.usedBy.map(code(_)).mkString(", \n"))
                    val nonVirtualFunctionCallIndex =
                        origin.asAssignment.targetVar.usedBy.iterator.filter(i ⇒ code(i).isNonVirtualMethodCall).toList.head
                    origin.asAssignment.targetVar.usedBy.size == 2 &&
                        code(nonVirtualFunctionCallIndex).asNonVirtualMethodCall.params.forall(isConstant(_))
                }
                case _ ⇒
                    // The value neither is a constant nor originates from a call, but if the
                    // current method does not take parameters and is deterministic, the value is
                    // guaranteed to be the same on every invocation.
                    lazyInitializerIsDeterministic(method, code)
            }
        }
        println(
            s"""
       | value.isConst : ${value.isConst}
       | isNonConstDeterminstic: ${isNonConstDeterministic(value)}
       |
       |""".stripMargin
        )

        val result = value.isConst || isNonConstDeterministic(value)
        println("check write is deterministic result: "+result)
        result
    }

    /**
     * Checks that all non-dead field reads that are not used for the guarding if-Statement of a
     * lazy initialization are only executed if the field did not have its default value or after
     * the (single) field write.
     */
    def checkReads(
        reads:        Seq[(Method, PCs)],
        readIndex:    Int,
        guardedIndex: Int,
        writeIndex:   Int,
        cfg:          CFG[Stmt[V], TACStmts[V]],
        pcToIndex:    Array[Int]
    ): Boolean = {
        // There is only a single method with reads aside from initializers (checked by
        // isLazilyInitialized), so we have to check only reads from that one method.
        reads.iterator.filter(!_._1.isInitializer).toList.head._2 forall { readPC: Int ⇒
            val index = pcToIndex(readPC)
            index != -1 || index == readIndex || checkRead(index, guardedIndex, writeIndex, cfg)
        }
    }

    /**
     * Checks that a field read is only executed if the field did not have its default value or
     * after the (single) field write.
     */
    def checkRead(
        readIndex:    Int,
        guardedIndex: Int,
        writeIndex:   Int,
        cfg:          CFG[Stmt[V], TACStmts[V]]
    ): Boolean = {
        val startBB = cfg.bb(readIndex).asBasicBlock
        val writeBB = cfg.bb(writeIndex)

        var enqueuedBBs: Set[CFGNode] = Set(startBB)
        var worklist: List[BasicBlock] = List(startBB.asBasicBlock)

        while (worklist.nonEmpty) {
            val curBB = worklist.head
            worklist = worklist.tail

            val startPC = curBB.startPC

            if (startPC == 0)
                return false; // Reached the start of the method but not the guard or field write

            if ((curBB eq writeBB) && writeIndex > readIndex)
                return false; // In the basic block of the write, but before the write

            if (startPC != guardedIndex && // Did not reach the guard
                (curBB ne writeBB) /* Not the basic block of the write */ ) {
                val predecessors = getPredecessors(curBB, enqueuedBBs)
                worklist ++= predecessors
                enqueuedBBs ++= predecessors
            }
        }

        true
    }

    /**
     * Checks if an expression is a field read of the currently analyzed field.
     * For instance fields, the read must be on the `this` reference.
     */

    def isReadOfCurrentField(expr: Expr[V], tacCode: TACode[TACMethodParameter, V])(implicit state: State): Boolean = {
        var seen: Set[Expr[V]] = Set.empty
        def _isReadOfCurrentField(expr: Expr[V], tacCode: TACode[TACMethodParameter, V])(implicit state: State): Boolean = {

            if (seen.contains(expr)) {
                return false
            };
            seen += expr
            import org.opalj.tac.Compare

            //println("params: "+expr.asVirtualFunctionCall.params.mkString(", "))
            //expr
            //val field:Option[Field] =
            (expr.astID: @switch) match {
                case GetField.ASTID ⇒
                    val objRefDefinition = expr.asGetField.objRef.asVar.definedBy
                    if (objRefDefinition != SelfReferenceParameter) false
                    else expr.asGetField.resolveField(project).contains(state.field)
                case GetStatic.ASTID ⇒ expr.asGetStatic.resolveField(project).contains(state.field)
                case Compare.ASTID ⇒ { //...........................................
                    val leftExpr = expr.asCompare.left
                    val rightExpr = expr.asCompare.right
                    val leftDefinitionIndex = leftExpr.asVar.definedBy.filter(i ⇒ i != expr.asCompare.pc).head
                    val rightDefinitionIndex = rightExpr.asVar.definedBy.filter(i ⇒ i != expr.asCompare.pc).head
                    if (leftDefinitionIndex < 0 || rightDefinitionIndex < 0)
                        return false;

                    val definitionStmtLeft = tacCode.stmts(leftDefinitionIndex)
                    val definitionStmtRight = tacCode.stmts(rightDefinitionIndex)
                    /*
                    println(
                        s"""
                       | leftExpr: $leftExpr

                       | rightExpr: $rightExpr


                       t: $definitionStmtLeft

                       : $definitionStmtRight

                       |
                     |""".stripMargin
                    ) */
                    if (definitionStmtLeft.asAssignment.expr.isGetField ||
                        definitionStmtLeft.asAssignment.expr.isGetStatic ||
                        definitionStmtLeft.asAssignment.expr.isVirtualFunctionCall) {
                        if (definitionStmtRight.asAssignment.expr.isConst) { //TODO ggf konservativer
                            _isReadOfCurrentField(definitionStmtLeft.asAssignment.expr, tacCode)
                        } else
                            false
                    } else {
                        if (definitionStmtLeft.asAssignment.expr.isConst) //TODO siehe oben
                            _isReadOfCurrentField(definitionStmtRight.asAssignment.expr, tacCode)
                        else
                            false
                    }

                }
                case VirtualFunctionCall.ASTID ⇒ {
                    val virtualFunctionCall = expr.asVirtualFunctionCall
                    val receiverDefSites = virtualFunctionCall.receiver.asVar.definedBy
                    //TODO better check of these functions
                    for {
                        defSite ← receiverDefSites
                    } {
                        if (defSite >= 0)
                            if (_isReadOfCurrentField(tacCode.stmts(defSite).asAssignment.expr, tacCode)) { //nothing to do
                            } else {
                                return false;
                            }
                        else {
                            return false;
                        }

                    }
                    true
                }
                case _ ⇒ false
            }
        }
        _isReadOfCurrentField(expr, tacCode)
    }

    /**
     * Determines if an if-Statement is actually a guard for the current field, i.e. it compares
     * the current field to the default value.
     */
    def isGuard(
        ifStmt:       If[V],
        defaultValue: Any,
        code:         Array[Stmt[V]],
        tacCode:      TACode[TACMethodParameter, V]
    )(implicit state: State): Boolean = {
        //println("check if is guard: "+ifStmt+", \n"+defaultValue)
        /**
         * Checks if an expression is an IntConst or FloatConst with the corresponding default value.
         */
        def isDefaultConst(expr: Expr[V]): Boolean = {

            if (expr.isVar) {
                val defSites = expr.asVar.definedBy
                val head = defSites.head
                defSites.size == 1 && head >= 0 && isDefaultConst(code(head).asAssignment.expr)
            } else {
                expr.isIntConst && defaultValue == expr.asIntConst.value ||
                    expr.isFloatConst && defaultValue == expr.asFloatConst.value ||
                    expr.isDoubleConst && defaultValue == expr.asDoubleConst || expr.isNullExpr && defaultValue == null
            }
        }

        /**
         * Checks whether the non-constant expression of the if-Statement is a read of the current
         * field.
         */
        def isGuardInternal(expr: V, tacCode: TACode[TACMethodParameter, V]): Boolean = {

            expr.definedBy forall { index ⇒
                if (index < 0) false // If the value is from a parameter, this can not be the guard
                else {
                    if (code(index).asAssignment.expr.isVirtualFunctionCall) {
                        //in case of Integer etc.... .initValue()
                        val virtualFunctionCall = code(index).asAssignment.expr.asVirtualFunctionCall
                        val callTargets = virtualFunctionCall.resolveCallTargets(state.field.classFile.thisType)
                        callTargets.foreach(
                            method ⇒ {
                                val propertyStorePurityResult = propertyStore(declaredMethods(method), Purity.key)
                                if (virtualFunctionCall.params.exists(!_.isConst) &&
                                    isNonDeterministic(propertyStorePurityResult))
                                    return false
                            }
                        )
                        if (callTargets.isEmpty || virtualFunctionCall.params.exists(!_.isConst))
                            return false;

                        val receiverDefSite = virtualFunctionCall.receiver.asVar.definedBy.head

                        receiverDefSite >= 0 && isReadOfCurrentField(
                            code(receiverDefSite).asAssignment.expr, tacCode
                        )
                    } else {
                        isReadOfCurrentField(code(index).asAssignment.expr, tacCode)
                    }

                }
            }
        }

        if ((ifStmt.rightExpr.isVar) && isDefaultConst(ifStmt.leftExpr)) {
            isGuardInternal(ifStmt.rightExpr.asVar, tacCode)
        } else if (ifStmt.leftExpr.isVar && isDefaultConst(ifStmt.rightExpr)) {
            isGuardInternal(ifStmt.leftExpr.asVar, tacCode)
        } else {
            false
        }
    }

    def checkWrites(
        write:        FieldWriteAccessStmt[V],
        writeIndex:   Int,
        guardIndex:   Int,
        guardedIndex: Int,
        method:       Method,
        code:         Array[Stmt[V]],
        cfg:          CFG[Stmt[V], TACStmts[V]]
    )(implicit state: State): Boolean = {
        val definitions = write.value.asVar.definedBy
        val isDeterministic =
            if (definitions.size == 1) {
                // The value written must be computed deterministically
                checkWriteIsDeterministic(code(definitions.head).asAssignment, method, code)
            } else {
                // More than one definition site for the value might lead to differences between
                // invocations, but not if this method has no parameters and is deterministic
                // (in this case, the definition reaching the write will always be the same)
                method.descriptor.parametersCount == 0 &&
                    !isNonDeterministic(propertyStore(declaredMethods(method), Purity.key))
            }
        val checkWriteIsGuardedResult =
            checkWriteIsGuarded(writeIndex, guardIndex, guardedIndex, method, code, cfg)
        // The field write must be guarded correctly
        isDeterministic && checkWriteIsGuardedResult

    }

    /**
     * Checks if the field write for a lazy initialization is immediately followed by a return of
     * the written value (possibly loaded by another field read).
     */
    def checkImmediateReturn(
        write:      FieldWriteAccessStmt[V],
        writeIndex: Int,
        readIndex:  Int,
        code:       Array[Stmt[V]],
        tacCode:    TACode[TACMethodParameter, V]
    )(implicit state: State): Boolean = {
        var index = writeIndex + 1

        var load = -1

        while (index < code.length) {
            val stmt = code(index)
            (stmt.astID: @switch) match {
                case Assignment.ASTID ⇒
                    if (isReadOfCurrentField(stmt.asAssignment.expr, tacCode))
                        load = index
                    else
                        return false; // No field read or a field read of a different field
                case ReturnValue.ASTID ⇒
                    val returnValueDefs = stmt.asReturnValue.expr.asVar.definedBy
                    if (returnValueDefs.size == 2 &&
                        returnValueDefs.contains(write.value.asVar.definedBy.head) &&
                        returnValueDefs.contains(readIndex))
                        return true; // direct return of the written value
                    else if (load >= 0 && (returnValueDefs == IntTrieSet(load) ||
                        returnValueDefs == IntTrieSet(readIndex, load)))
                        return true; // return of field value loaded by field read
                    else
                        return false; // return of different value
                case _ ⇒ return false; // neither a field read nor a return
            }
            index += 1
        }
        false
    }

    def checkThatTheValueOfTheFieldIsReturned(
        write:      FieldWriteAccessStmt[V],
        writeIndex: Int,
        readIndex:  Int,
        code:       Array[Stmt[V]],
        tacCode:    TACode[TACMethodParameter, V]
    )(implicit state: State): Boolean = {
        var index = writeIndex + 1

        var load = -1

        while (index < code.length) {
            val stmt = code(index)
            (stmt.astID: @switch) match {
                case a @ Assignment.ASTID ⇒
                    if (isReadOfCurrentField(stmt.asAssignment.expr, tacCode)) {
                        load = index
                    }
                // No field read or a field read of a different field
                case ReturnValue.ASTID ⇒
                    val returnValueDefs = stmt.asReturnValue.expr.asVar.definedBy
                    if (returnValueDefs.size == 2 &&
                        returnValueDefs.contains(write.value.asVar.definedBy.head) &&
                        returnValueDefs.contains(readIndex)) {
                        return true
                    }; // direct return of the written value
                    else if (load >= 0 && (returnValueDefs == IntTrieSet(load) ||
                        returnValueDefs == IntTrieSet(readIndex, load))) {
                        return true
                    }; // return of field value loaded by field read
                    else {
                        return false
                    }; // return of different value
                case _ ⇒ ;
            }
            index += 1
        }
        false
    }
}
