/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac

import scala.collection.concurrent.TrieMap
import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.ProjectInformationKey
import org.opalj.ai.domain.l1.DefaultDomainWithCFGAndDefUse
import org.opalj.ai.BaseAI
import org.opalj.value.KnownTypedValue

/**
 * ''Key'' to get the 3-address based code of a method computed using the configured
 * domain/data-flow analysis. This key performs the underlying data-flow analysis on demand using
 * the configured data-flow analyses; the results of the data-flow analyses are NOT cached/shared.
 * Hence, this ''key'' should only be used if the result of the underlying analysis is no longer
 * required after generating the TAC.
 *
 * @example To get the index use the [[org.opalj.br.analyses.Project]]'s `get` method and
 *          pass in `this` object.
 *
 * @author Michael Eichberg
 */
object SimpleTACAIKey extends TACAIKey {

    /**
     * TACAI code has no special prerequisites.
     */
    override protected def requirements: Seq[ProjectInformationKey[Nothing, Nothing]] = Nil

    /**
     * Returns an object which computes and caches the 3-address code of a method when required.
     *
     * All methods belonging to a project are converted using the same `domainFactory`. Hence,
     * the `domainFactory` needs to be set before compute is called/this key is passed to a
     * specific project. If multiple projects are instead concurrently, external synchronization
     * is necessary (e.g., on the ProjectInformationKey) to ensure that each project is
     * instantiated using the desired domain.
     */
    override protected def compute(
        project: SomeProject
    ): Method ⇒ TACode[TACMethodParameter, DUVar[KnownTypedValue]] = {
        val domainFactory = project.
            getProjectInformationKeyInitializationData(this).
            getOrElse((m: Method) ⇒ new DefaultDomainWithCFGAndDefUse(project, m))

        val taCodes = TrieMap.empty[Method, TACode[TACMethodParameter, DUVar[KnownTypedValue]]]

        def computeAndCacheTAC(m: Method) = {
            val domain = domainFactory(m)
            val aiResult = BaseAI(m, domain)
            val code = TACAI(m, project.classHierarchy, aiResult)(Nil)
            // well... the following cast safe is safe, because the underlying
            // datastructure is actually, conceptually immutable
            val taCode = code.asInstanceOf[TACode[TACMethodParameter, DUVar[KnownTypedValue]]]
            taCodes.put(m, taCode)
            taCode
        }

        (m: Method) ⇒ taCodes.get(m) match {
            case Some(taCode) ⇒ taCode
            case None ⇒
                val brCode = m.body.get
                // Basically, we use double checked locking; we really don't want to
                // transform the code more than once!
                brCode.synchronized {
                    taCodes.get(m) match {
                        case Some(taCode) ⇒ taCode
                        case None         ⇒ computeAndCacheTAC(m)
                    }
                }

        }
    }
}
