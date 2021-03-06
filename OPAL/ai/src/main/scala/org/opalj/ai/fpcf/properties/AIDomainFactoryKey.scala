/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.ai
package fpcf
package properties

import org.opalj.log.OPALLogger
import org.opalj.br.Method
import org.opalj.br.analyses.ProjectInformationKey
import org.opalj.br.analyses.SomeProject
import org.opalj.ai.common.DomainRegistry

class ProjectSpecificAIExecutor(
        val project:       SomeProject,
        val domainClass:   Class[_ <: Domain],
        val domainFactory: (SomeProject, Method) ⇒ Domain
) extends (Method ⇒ AIResult) {

    def apply(m: Method): AIResult = { BaseAI(m, domainFactory(project, m)) }
}

/**
 * Key to get the factory to create the domains that are used to perform abstract interpretations.
 * The domain that is going to be used is determined by getting the set of (partial)domains
 * that are required and then computing the cheapest domain;
 * see [[org.opalj.ai.common.DomainRegistry]] for further information.
 * Hence, the AIResult's domain is guaranteed to implement all required (partial) domains.
 *
 * @author Michael Eichberg
 */
object AIDomainFactoryKey
    extends ProjectInformationKey[ProjectSpecificAIExecutor, Set[Class[_ <: AnyRef]]] {

    /**
     * This key has no special prerequisites.
     *
     * @note The configuration is done using '''ProjectInformationKeyInitializationData'''.
     */
    override protected def requirements: Seq[ProjectInformationKey[Nothing, Nothing]] = Nil

    /**
     * Returns an object which performs and caches the result of the abstract interpretation of a
     * method when required.
     *
     * All methods belonging to a project are analyzed using the same `domainFactory`. Hence,
     * the `domainFactory` needs to be set before compute is called/this key is passed to a
     * specific project. If multiple projects are instead concurrently, external synchronization
     * is necessary (e.g., on the ProjectInformationKey) to ensure that each project is
     * instantiated using the desired domain.
     */
    override protected def compute(project: SomeProject): ProjectSpecificAIExecutor = {
        implicit val logContext = project.logContext

        val domainFactoryRequirements = project.
            getProjectInformationKeyInitializationData(this).
            getOrElse(Set.empty)

        val domainFactories =
            DomainRegistry.selectConfigured(project.config, domainFactoryRequirements)

        if (domainFactories.isEmpty) {
            val message = domainFactoryRequirements.mkString(
                "no abstract domain that satisfies the requirements: {", ", ", "} exists."
            )
            throw new IllegalArgumentException(message)
        }
        if (domainFactories.size > 1) {
            OPALLogger.info(
                "analysis configuration",
                s"multiple domains ${domainFactories.mkString(", ")} "+
                    s"satisfy the requirements ${domainFactoryRequirements.mkString(", ")} "
            )
        }

        val domainClass = domainFactories.head
        OPALLogger.info(
            "analysis configuration",
            s"the domain $domainClass will be used for performing abstract interpretations"
        )

        val domainFactory = DomainRegistry.domainMetaInformation(domainClass).factory
        new ProjectSpecificAIExecutor(project, domainClass, domainFactory)
    }
}
