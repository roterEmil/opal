/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.analyses.string_definition.expr_processing
import org.opalj.br.cfg.CFG
import org.opalj.fpcf.analyses.string_definition.V
import org.opalj.fpcf.string_definition.properties.StringTree
import org.opalj.fpcf.string_definition.properties.TreeConditionalElement
import org.opalj.fpcf.string_definition.properties.TreeElement
import org.opalj.tac.ArrayLoad
import org.opalj.tac.ArrayStore
import org.opalj.tac.Assignment
import org.opalj.tac.Expr
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts

import scala.collection.mutable.ListBuffer

/**
 * This implementation of [[AbstractExprProcessor]] processes [[org.opalj.tac.ArrayLoad]]
 * expressions.
 *
 * @param exprHandler As this expression processor will encounter other expressions outside its
 *                    scope, such as StringConst or NonVirtualFunctionCall, an [[ExprHandler]] is
 *                    required.
 *
 * @author Patrick Mell
 */
class ArrayLoadProcessor(
        private val exprHandler: ExprHandler
) extends AbstractExprProcessor {

    /**
     * The `expr` of `assignment`is required to be of type [[org.opalj.tac.ArrayLoad]] (otherwise
     * `None` will be returned).
     *
     * @see [[AbstractExprProcessor.processAssignment]]
     */
    override def processAssignment(
        assignment: Assignment[V], stmts: Array[Stmt[V]], cfg: CFG[Stmt[V], TACStmts[V]],
        ignore: List[Int] = List[Int]()
    ): Option[StringTree] = process(assignment.expr, stmts, ignore)

    /**
     * The `expr` of `assignment`is required to be of type [[org.opalj.tac.ArrayLoad]] (otherwise
     * * `None` will be returned).
     * *
     * * @see [[AbstractExprProcessor.processExpr]]
     */
    override def processExpr(
        expr: Expr[V], stmts: Array[Stmt[V]], cfg: CFG[Stmt[V], TACStmts[V]],
        ignore: List[Int] = List[Int]()
    ): Option[StringTree] = process(expr, stmts, ignore)

    /**
     * Wrapper function for processing an expression.
     */
    private def process(
        expr: Expr[V], stmts: Array[Stmt[V]], ignore: List[Int]
    ): Option[StringTree] = {
        expr match {
            case al: ArrayLoad[V] ⇒
                val children = ListBuffer[TreeElement]()
                // Loop over all possible array values
                al.arrayRef.asVar.definedBy.foreach { defSite ⇒
                    if (!ignore.contains(defSite)) {
                        val arrDecl = stmts(defSite)
                        arrDecl.asAssignment.targetVar.usedBy.filter {
                            stmts(_).isInstanceOf[ArrayStore[V]]
                        } foreach { f: Int ⇒
                            // Actually, definedBy should contain only one element but for the sake
                            // of completion, loop over all
                            // TODO: If not, the tree construction has to be modified
                            val arrValues = stmts(f).asArrayStore.value.asVar.definedBy.map {
                                exprHandler.processDefSite _
                            }.filter(_.isDefined).map(_.get)
                            children.appendAll(arrValues)
                        }
                    }
                }

                Some(TreeConditionalElement(children))
            case _ ⇒ None
        }
    }

}
