/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.opalj.br.DeclaredMethod
import org.opalj.br.MethodDescriptor
import org.opalj.br.ObjectType
import org.opalj.br.analyses.ProjectInformationKey
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.cg.ClassExtensibilityKey
import org.opalj.fpcf.properties.Purity

class ConfiguredPurity(
        project:         SomeProject,
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
) {
    private case class PurityValue(
            cf:    String,
            m:     String,
            desc:  String,
            p:     String,
            conds: Option[Seq[String]]
    )

    private val classExtensibility = project.get(ClassExtensibilityKey)

    private val toSet = project.config.as[Seq[PurityValue]](
        "org.opalj.fpcf.analyses.ConfiguredPurity.purities"
    )

    private val methods: Set[DeclaredMethod] =
        for {
            PurityValue(className, methodName, descriptor, property, conditions) ← toSet.toSet

            po = Purity(property)
            if po.isDefined

            if conditions forall {
                _ forall { typeName ⇒
                    val ot = ObjectType(typeName)
                    project.classHierarchy.hasSubtypes(ot).isNo && classExtensibility(ot).isNo
                }
            }

            mdo = if (descriptor == "*") None else Some(MethodDescriptor(descriptor))

            ms = if (className == "*") {
                project.allMethods.filter { m ⇒
                    m.name == methodName && mdo.forall(_ == m.descriptor)
                }.map(declaredMethods(_))
            } else {
                val classType = ObjectType(className)
                val cfo = project.classFile(classType)

                mdo match {
                    case Some(md) ⇒ Seq(
                        declaredMethods(classType, classType.packageName, classType, methodName, md)
                    )
                    case None ⇒ cfo.map { cf ⇒
                        cf.findMethod(methodName).map(declaredMethods(_)).toIterable
                    }.getOrElse(Seq.empty)

                }
            }

            dm ← ms
        } yield {
            propertyStore.set(dm, po.get)
            dm
        }

    propertyStore.waitOnPhaseCompletion() // wait until setting configured purities is completed

    def wasSet(dm: DeclaredMethod): Boolean = {
        methods.contains(dm)
    }
}

object ConfiguredPurityKey extends ProjectInformationKey[ConfiguredPurity, Nothing] {

    def requirements = Seq(PropertyStoreKey, DeclaredMethodsKey)

    override protected def compute(project: SomeProject): ConfiguredPurity = {
        new ConfiguredPurity(
            project,
            project.get(PropertyStoreKey),
            project.get(DeclaredMethodsKey)
        )
    }
}
