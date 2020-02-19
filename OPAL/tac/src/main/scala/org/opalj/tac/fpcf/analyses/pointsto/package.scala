/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses

import org.opalj.fpcf.Entity
import org.opalj.value.ValueInformation
import org.opalj.br.DefinedMethod
import org.opalj.br.analyses.VirtualFormalParameters
import org.opalj.tac.common.DefinitionSite
import org.opalj.tac.common.DefinitionSites

package object pointsto {

    /**
     * Given a definition site (value origin) in a certain method, this returns the
     * entity to be used to attach/retrieve points-to information from.
     */
    def toEntity(
        defSite: Int, method: DefinedMethod, stmts: Array[Stmt[DUVar[ValueInformation]]]
    )(
        implicit
        formalParameters: VirtualFormalParameters, definitionSites: DefinitionSites
    ): Entity = {
        if (ai.isMethodExternalExceptionOrigin(defSite)) {
            val pc = ai.pcOfMethodExternalException(defSite)
            CallExceptions(toEntity(pc, method, stmts).asInstanceOf[DefinitionSite])
        } else if (ai.isImmediateVMException(defSite)) {
            null // TODO Implement
        } else if (defSite < 0) {
            formalParameters.apply(method)(-1 - defSite)
        } else {
            definitionSites(method.definedMethod, stmts(defSite).pc)
        }
    }
}
