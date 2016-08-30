/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package br
package reader

import java.util.concurrent.atomic.AtomicInteger
import org.opalj.br.instructions._
import com.typesafe.config.Config
import org.opalj.log.LogContext
import org.opalj.log.OPALLogger

/**
 * Provides full support for rewriting Java 8 lambda or method reference expressions that
 * are translated to [[INVOKEDYNAMIC]] instructions. This trait should be mixed in alongside a
 * [[BytecodeReaderAndBinding]], which extracts basic `invokedynamic` information from the
 * [[BootstrapMethodTable]].
 *
 * Specifically, whenever an `invokedynamic` instruction is encountered that is the result
 * of a lambda/method reference expression compiled by Oracle's JDK8, it creates a proxy
 * class file that represents the synthetic object that the JVM generates after executing
 * the `invokedynamic` call site. This proxy is then stored in the temporary ClassFile
 * attribute [[SynthesizedClassFiles]]. All such ClassFiles will
 * be picked up later for inclusion in the project.
 *
 * @author Arne Lottmann
 * @author Michael Eichberg
 */
trait Java8LambdaExpressionsRewriting extends DeferredInvokedynamicResolution {
    this: ClassFileBinding ⇒

    /**
     * The `Config` object that will be used to read the setting whether JDK8 invokedynamic
     * calls should be rewritten or not.
     */
    def config: Config

    implicit def logContext: LogContext

    val performJava8LambdaExpressionsRewriting: Boolean = {
        import Java8LambdaExpressionsRewriting.{Java8LambdaExpressionsRewritingConfigKey ⇒ Key}
        import net.ceedubs.ficus.Ficus._
        val doRewrite = config.as[Option[Boolean]](Key).getOrElse(false)
        if (doRewrite) {
            OPALLogger.info("project configuration", "Java 8 lambda expressions are rewritten")
        } else {
            OPALLogger.info("project configuration", "Java 8 lambda expressions are not resolved")
        }
        doRewrite
    }

    /**
     * Counter to ensure that the generated types have unique names.
     */
    private final val typeIdGenerator = new AtomicInteger(0)

    /**
     * Generates a new, internal name for a lambda expression found in the given
     * `surroundingType`.
     *
     * It follows the pattern: `Lambda\$${surroundingType.id}:${uniqueId}`, where
     * `uniqueId` is simply a run-on counter. For example: `Lambda$17:4` would refer to
     * the fourth Lambda INVOKEDYNAMIC parsed during the analysis of the project, which
     * is defined in the [[ClassFile]] with the type id `17`.
     *
     * @param surroundingType the type in which the Lambda expression has been found
     */
    private def newLambdaTypeName(surroundingType: ObjectType): String = {
        val nextId = typeIdGenerator.getAndIncrement()
        s"Lambda\$${surroundingType.id}:${nextId}"
    }

    override def deferredInvokedynamicResolution(
        classFile:         ClassFile,
        cp:                Constant_Pool,
        invokeDynamicInfo: CONSTANT_InvokeDynamic_info,
        instructions:      Array[Instruction],
        index:             Int
    ): ClassFile = {

        // gather complete information about invokedynamic instructions from the bootstrap
        // method table
        var updatedClassFile =
            super.deferredInvokedynamicResolution(
                classFile,
                cp,
                invokeDynamicInfo,
                instructions,
                index
            )

        val invokedynamic = instructions(index).asInstanceOf[INVOKEDYNAMIC]
        if (isJDK8Invokedynamic(invokedynamic)) {
            val INVOKEDYNAMIC(
                bootstrapMethod,
                functionalInterfaceMethodName,
                factoryDescriptor) = invokedynamic
            val bootstrapArguments = bootstrapMethod.arguments
            // apparently there are cases in the JRE where there are more than just those
            // three parameters
            val Seq(
                functionalInterfaceDescriptorAfterTypeErasure: MethodDescriptor,
                invokeTargetMethodHandle: MethodCallMethodHandle,
                functionalInterfaceDescriptorBeforeTypeErasure: MethodDescriptor, _*) =
                bootstrapArguments
            val MethodCallMethodHandle(
                targetMethodOwner: ObjectType,
                targetMethodName,
                targetMethodDescriptor) = invokeTargetMethodHandle

            val superInterface: Set[ObjectType] = Set(factoryDescriptor.returnType.asObjectType)
            val typeDeclaration = TypeDeclaration(
                ObjectType(newLambdaTypeName(targetMethodOwner)),
                isInterfaceType = false,
                Some(ObjectType.Object),
                superInterface
            )

            val invocationInstruction = invokeTargetMethodHandle.opcodeOfUnderlyingInstruction

            val receiverDescriptor: MethodDescriptor =
                if (invokeTargetMethodHandle.isInstanceOf[NewInvokeSpecialMethodHandle]) {
                    MethodDescriptor(
                        targetMethodDescriptor.parameterTypes,
                        targetMethodOwner
                    )
                } else {
                    targetMethodDescriptor
                }

            val needsBridgeMethod = functionalInterfaceDescriptorAfterTypeErasure !=
                functionalInterfaceDescriptorBeforeTypeErasure

            val bridgeMethodDescriptor: Option[MethodDescriptor] =
                if (needsBridgeMethod) {
                    Some(functionalInterfaceDescriptorAfterTypeErasure)
                } else None

            val proxy: ClassFile = ClassFileFactory.Proxy(
                typeDeclaration,
                functionalInterfaceMethodName,
                functionalInterfaceDescriptorBeforeTypeErasure,
                targetMethodOwner,
                receiverIsInterface = false,
                targetMethodName,
                receiverDescriptor,
                invocationInstruction,
                bridgeMethodDescriptor
            )
            val factoryMethod =
                if (functionalInterfaceMethodName == ClassFileFactory.DefaultFactoryMethodName)
                    proxy.findMethod(ClassFileFactory.AlternativeFactoryMethodName).get
                else
                    proxy.findMethod(ClassFileFactory.DefaultFactoryMethodName).get

            // TODO: log this replacement sometime in the future
            instructions(index) = INVOKESTATIC(
                proxy.thisType,
                isInterface = false,
                factoryMethod.name,
                factoryMethod.descriptor
            )
            // since invokestatic is two bytes shorter than invokedynamic, we need to fill
            // the two-byte gap following the invokestatic with NOPs
            // TODO [Compact Code] Remove the addition of the following two NOPS!
            instructions(index + 3) = NOP
            instructions(index + 4) = NOP

            updatedClassFile = storeProxy(updatedClassFile, proxy)
        }

        updatedClassFile
    }

    /**
     * Descriptor of the method `java.lang.invoke.LambdaMetafactory.metafactory`.
     */
    val lambdaMetafactoryDescriptor = MethodDescriptor(
        IndexedSeq(
            ObjectType.MethodHandles$Lookup,
            ObjectType.String,
            ObjectType.MethodType,
            ObjectType.MethodType,
            ObjectType.MethodHandle,
            ObjectType.MethodType
        ),
        ObjectType.CallSite
    )

    /**
     * Descriptor of the method `java.lang.invoke.LambdaMetafactory.altMetafactory`.
     */
    val lambdaAltMetafactoryDescriptor = MethodDescriptor(
        IndexedSeq(
            ObjectType.MethodHandles$Lookup,
            ObjectType.String,
            ObjectType.MethodType,
            ArrayType.ArrayOfObjects
        ),
        ObjectType.CallSite
    )

    def isJDK8Invokedynamic(invokedynamic: INVOKEDYNAMIC): Boolean = {
        val bootstrapMethodHandle = invokedynamic.bootstrapMethod.handle
        if (!bootstrapMethodHandle.isInvokeStaticMethodHandle)
            return false;

        val InvokeStaticMethodHandle(receiver, isInterface, name, descriptor) =
            bootstrapMethodHandle

        receiver == ObjectType.LambdaMetafactory && !isInterface && (
            (name == "metafactory" && descriptor == lambdaMetafactoryDescriptor) ||
            (name == "altMetafactory" && descriptor == lambdaAltMetafactoryDescriptor)
        )
    }

    def storeProxy(classFile: ClassFile, proxy: ClassFile): ClassFile = {
        classFile.attributes.collectFirst {
            case scf @ SynthesizedClassFiles(proxies) ⇒ {
                val newScf = new SynthesizedClassFiles(proxies :+ proxy)
                val newAttributes = newScf +: classFile.attributes.filterNot(_ eq scf)
                classFile.copy(attributes = newAttributes)
            }
            case _ ⇒ {
                val newAttributes = new SynthesizedClassFiles(Seq(proxy)) +: classFile.attributes
                classFile.copy(attributes = newAttributes)
            }
        }.get
    }
}

object Java8LambdaExpressionsRewriting {
    final val Java8LambdaExpressionsRewritingConfigKey = {
        "org.opalj.br.reader.Java8LambdaExpressionsRewriting"
    }
}
