/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses

import org.opalj.collection.immutable.IntTrieSet
import org.opalj.collection.immutable.EmptyIntTrieSet
import org.opalj.value.ValueInformation
import org.opalj.br.PCs
import org.opalj.ai.ValueOrigin
import org.opalj.ai.pcOfImmediateVMException
import org.opalj.ai.pcOfMethodExternalException
import org.opalj.ai.ValueOriginForImmediateVMException
import org.opalj.ai.ValueOriginForMethodExternalException
import org.opalj.ai.MethodExternalExceptionsOriginOffset
import org.opalj.ai.ImmediateVMExceptionsOriginOffset
import org.opalj.ai.isMethodExternalExceptionOrigin
import org.opalj.ai.isImmediateVMException
import org.opalj.br.MethodDescriptor
import org.opalj.br.ReferenceType

package object cg {

    type V = DUVar[ValueInformation]

    type CallSiteT = (Int /*PC*/ , String, MethodDescriptor, ReferenceType)

    /**
     * A persistent representation (using pcs instead of TAC value origins) for a UVar.
     */
    final def persistentUVar(
        value: V
    )(
        implicit
        stmts: Array[Stmt[V]]
    ): Some[(ValueInformation, IntTrieSet)] = {
        Some((value.value, value.definedBy.map(pcOfDefSite _)))
    }

    final def pcOfDefSite(valueOrigin: ValueOrigin)(implicit stmts: Array[Stmt[V]]): Int = {
        if (valueOrigin >= 0)
            stmts(valueOrigin).pc
        else if (valueOrigin > ImmediateVMExceptionsOriginOffset)
            valueOrigin // <- it is a parameter!
        else if (valueOrigin > MethodExternalExceptionsOriginOffset)
            ValueOriginForImmediateVMException(stmts(pcOfImmediateVMException(valueOrigin)).pc)
        else
            ValueOriginForMethodExternalException(
                stmts(pcOfMethodExternalException(valueOrigin)).pc
            )
    }

    final def valueOriginsOfPCs(pcs: PCs, pcToIndex: Array[Int]): IntTrieSet = {
        /* OLD
        val origins = new IntTrieSetBuilder
        PCs.iterator.collect {
            case pc if ai.underlyingPC(pc) < 0       ⇒ pc // parameter
            case pc if pc >= 0 && pcToIndex(pc) >= 0 ⇒ pcToIndex(pc) // local
            case pc if isImmediateVMException(pc) && pcToIndex(pcOfImmediateVMException(pc)) >= 0 ⇒
                ValueOriginForImmediateVMException(pcToIndex(pcOfImmediateVMException(pc)))
            case pc if isMethodExternalExceptionOrigin(pc) && pcToIndex(pcOfMethodExternalException(pc)) >= 0 ⇒
                ValueOriginForMethodExternalException(pcToIndex(pcOfMethodExternalException(pc)))
        } foreach { origins += _ }
        origins.result()
        */
        pcs.foldLeft(EmptyIntTrieSet: IntTrieSet) { (origins, pc) ⇒
            if (ai.underlyingPC(pc) < 0)
                origins + pc // parameter
            else if (pc >= 0 && pcToIndex(pc) >= 0)
                origins + pcToIndex(pc) // local
            else if (isImmediateVMException(pc) && pcToIndex(pcOfImmediateVMException(pc)) >= 0)
                origins + ValueOriginForImmediateVMException(pcToIndex(pcOfImmediateVMException(pc)))
            else if (isMethodExternalExceptionOrigin(pc) && pcToIndex(pcOfMethodExternalException(pc)) >= 0)
                origins + ValueOriginForMethodExternalException(pcToIndex(pcOfMethodExternalException(pc)))
            else
                origins // as is
        }
    }

    final def uVarForDefSites(
        defSites:  (ValueInformation, IntTrieSet),
        pcToIndex: Array[Int]
    ): V = {
        UVar(defSites._1, valueOriginsOfPCs(defSites._2, pcToIndex))
    }
}