/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
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
package da

import scala.reflect.ClassTag
import org.opalj.bi.AttributeParent

/**
 * Factory methods to read class files and create [[ClassFile]] objects.
 *
 * @author Michael Eichberg
 * @author Wael Alkhatib
 * @author Isbel Isbel
 * @author Noorulla Sharief
 */
object ClassFileReader
        extends Constant_PoolBinding
        with bi.reader.ClassFileReader
        with bi.reader.FieldsReader
        with bi.reader.MethodsReader
        with bi.reader.AttributesReader
        with bi.reader.Unknown_attributeReader
        with bi.reader.BootstrapMethods_attributeReader
        with bi.reader.Code_attributeReader
        with bi.reader.CodeReader
        with bi.reader.SourceFile_attributeReader
        with bi.reader.Signature_attributeReader
        with bi.reader.ConstantValue_attributeReader
        with bi.reader.Synthetic_attributeReader
        with bi.reader.Deprecated_attributeReader
        with bi.reader.SourceDebugExtension_attributeReader
        with bi.reader.InnerClasses_attributeReader
        with bi.reader.Exceptions_attributeReader
        with bi.reader.EnclosingMethod_attributeReader
        with bi.reader.LineNumberTable_attributeReader
        with bi.reader.LocalVariableTable_attributeReader
        with bi.reader.LocalVariableTypeTable_attributeReader
        with bi.reader.ElementValuePairsReader
        with bi.reader.ParametersAnnotationsReader
        with bi.reader.MethodParameters_attributeReader
        with bi.reader.AnnotationsReader
        with bi.reader.AnnotationDefault_attributeReader
        with bi.reader.RuntimeVisibleAnnotations_attributeReader
        with bi.reader.RuntimeInvisibleAnnotations_attributeReader
        with bi.reader.RuntimeVisibleParameterAnnotations_attributeReader
        with bi.reader.RuntimeInvisibleParameterAnnotations_attributeReader
        with bi.reader.VerificationTypeInfoReader
        with bi.reader.StackMapTable_attributeReader
        with bi.reader.StackMapFrameReader
        with bi.reader.TypeAnnotationTargetReader
        with bi.reader.RuntimeInvisibleTypeAnnotations_attributeReader
        with bi.reader.RuntimeVisibleTypeAnnotations_attributeReader
        with bi.reader.TypeAnnotationPathReader
        with bi.reader.TypeAnnotationsReader
        // JAVA 9
        with bi.reader.Module_attributeReader
        with bi.reader.Version_attributeReader
        with bi.reader.MainClass_attributeReader
        with bi.reader.ConcealedPackages_attributeReader
        with bi.reader.TargetPlatform_attributeReader {

    type CPIndex = Constant_Pool_Index

    type ClassFile = da.ClassFile

    type Attribute = da.Attribute
    val AttributeManifest: ClassTag[Attribute] = implicitly

    type Field_Info = da.Field_Info
    val Field_InfoManifest: ClassTag[Field_Info] = implicitly

    type Method_Info = da.Method_Info
    val Method_InfoManifest: ClassTag[Method_Info] = implicitly

    final override def reifyEmptyAttributes: Boolean = true

    def ClassFile(
        cp:            Constant_Pool,
        minor_version: Int, major_version: Int,
        access_flags: Int,
        this_class:   Constant_Pool_Index, super_class: Constant_Pool_Index, interfaces: Interfaces,
        fields:     Fields,
        methods:    Methods,
        attributes: Attributes
    ): ClassFile =
        new ClassFile(
            cp, minor_version, major_version, access_flags,
            this_class, super_class, interfaces, fields, methods, attributes
        )

    def Field_Info(
        cp:               Constant_Pool,
        access_flags:     Int,
        name_index:       Constant_Pool_Index,
        descriptor_index: Constant_Pool_Index,
        attributes:       Attributes
    ): Field_Info =
        new Field_Info(access_flags, name_index, descriptor_index, attributes)

    def Method_Info(
        cp:               Constant_Pool,
        access_flags:     Int,
        name_index:       Constant_Pool_Index,
        descriptor_index: Constant_Pool_Index,
        attributes:       Attributes
    ): Method_Info =
        new Method_Info(access_flags, name_index, descriptor_index, attributes)

    type SourceFile_attribute = da.SourceFile_attribute
    def SourceFile_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        sourceFile_index:     Constant_Pool_Index
    ): SourceFile_attribute =
        new SourceFile_attribute(attribute_name_index, sourceFile_index)

    type Signature_attribute = da.Signature_attribute
    def Signature_attribute(
        cp: Constant_Pool, ap: AttributeParent, attribute_name_index: Int,
        signature_index: Int
    ): Signature_attribute =
        new Signature_attribute(attribute_name_index, signature_index)

    type ConstantValue_attribute = da.ConstantValue_attribute
    def ConstantValue_attribute(
        cp: Constant_Pool, attribute_name_index: Int,
        constantvalue_index: Int
    ): ConstantValue_attribute =
        new ConstantValue_attribute(attribute_name_index, constantvalue_index)

    type Synthetic_attribute = da.Synthetic_attribute
    def Synthetic_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index
    ): Synthetic_attribute =
        new Synthetic_attribute(attribute_name_index)

    type Deprecated_attribute = da.Deprecated_attribute
    def Deprecated_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index
    ): Deprecated_attribute =
        new Deprecated_attribute(attribute_name_index)

    type SourceDebugExtension_attribute = da.SourceDebugExtension_attribute
    def SourceDebugExtension_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        debug_extension:      Array[Byte]
    ): SourceDebugExtension_attribute =
        new SourceDebugExtension_attribute(attribute_name_index, debug_extension)

    type BootstrapMethods_attribute = da.BootstrapMethods_attribute

    type BootstrapMethod = da.BootstrapMethod
    implicit val BootstrapMethodManifest: ClassTag[BootstrapMethod] = implicitly

    type BootstrapArgument = da.BootstrapArgument
    implicit val BootstrapArgumentManifest: ClassTag[BootstrapArgument] = implicitly

    def BootstrapMethods_attribute(
        constant_pool:        Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        bootstrap_methods:    BootstrapMethods
    ): BootstrapMethods_attribute =
        new BootstrapMethods_attribute(attribute_name_index, bootstrap_methods)

    def BootstrapMethod(
        cp:         Constant_Pool,
        method_ref: Int,
        arguments:  BootstrapArguments
    ): BootstrapMethod =
        new BootstrapMethod(method_ref, arguments)

    def BootstrapArgument(
        cp:     Constant_Pool,
        cp_ref: Int
    ): BootstrapArgument =
        new BootstrapArgument(cp_ref)

    val InnerClassesEntryManifest: ClassTag[InnerClassesEntry] = implicitly

    type InnerClasses_attribute = da.InnerClasses_attribute
    def InnerClasses_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        classes: InnerClasses
    ): InnerClasses_attribute =
        new InnerClasses_attribute(attribute_name_index, classes)

    type InnerClassesEntry = da.InnerClassesEntry
    def InnerClassesEntry(
        cp:                       Constant_Pool,
        inner_class_info_index:   Constant_Pool_Index,
        outer_class_info_index:   Constant_Pool_Index,
        inner_name_index:         Constant_Pool_Index,
        inner_class_access_flags: Constant_Pool_Index
    ): InnerClassesEntry =
        new InnerClassesEntry(
            inner_class_info_index, outer_class_info_index,
            inner_name_index,
            inner_class_access_flags
        )

    val Exceptions_attributeManifest: ClassTag[Exceptions_attribute] = implicitly

    type Exceptions_attribute = da.Exceptions_attribute
    def Exceptions_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        exception_index_table: ExceptionIndexTable
    ): Exceptions_attribute =
        new Exceptions_attribute(attribute_name_index, exception_index_table)

    val ExceptionTableEntryManifest: ClassTag[ExceptionTableEntry] = implicitly

    type Instructions = da.Code
    def Instructions(
        cp:           Constant_Pool,
        instructions: Array[Byte]
    ): Instructions =
        new Instructions(instructions)

    type ExceptionTableEntry = da.ExceptionTableEntry
    def ExceptionTableEntry(
        cp:       Constant_Pool,
        start_pc: Int, end_pc: Int, handler_pc: Int, catch_type: Int
    ): ExceptionTableEntry =
        new ExceptionTableEntry(start_pc, end_pc, handler_pc, catch_type)

    type Code_attribute = da.Code_attribute
    def Code_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        max_stack: Int, max_locals: Int,
        instructions:    Instructions,
        exception_table: ExceptionHandlers,
        attributes:      Attributes
    ): Code_attribute =
        new Code_attribute(
            attribute_name_index,
            max_stack, max_locals,
            instructions,
            exception_table,
            attributes
        )

    type Unknown_attribute = da.Unknown_attribute
    def Unknown_attribute(
        constant_pool:        Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        info:                 Array[Byte]
    ): Unknown_attribute = {
        new Unknown_attribute(attribute_name_index, info)
    }

    type EnclosingMethod_attribute = da.EnclosingMethod_attribute
    def EnclosingMethod_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        class_index:  Constant_Pool_Index,
        method_index: Constant_Pool_Index
    ): EnclosingMethod_attribute =
        new EnclosingMethod_attribute(attribute_name_index, class_index, method_index)

    type LineNumberTable_attribute = da.LineNumberTable_attribute
    def LineNumberTable_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        line_number_table:    LineNumbers
    ): LineNumberTable_attribute =
        new LineNumberTable_attribute(attribute_name_index, line_number_table)

    val LineNumberTableEntryManifest: ClassTag[LineNumberTableEntry] = implicitly
    type LineNumberTableEntry = da.LineNumberTableEntry
    def LineNumberTableEntry(start_pc: Int, line_number: Int): LineNumberTableEntry =
        new LineNumberTableEntry(start_pc, line_number)

    type LocalVariableTable_attribute = da.LocalVariableTable_attribute
    def LocalVariableTable_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        local_variable_table: LocalVariables
    ): LocalVariableTable_attribute =
        new LocalVariableTable_attribute(attribute_name_index, local_variable_table)

    val LocalVariableTableEntryManifest: ClassTag[LocalVariableTableEntry] = implicitly
    type LocalVariableTableEntry = da.LocalVariableTableEntry
    def LocalVariableTableEntry(
        cp:               Constant_Pool,
        start_pc:         Int,
        length:           Int,
        name_index:       Constant_Pool_Index,
        descriptor_index: Constant_Pool_Index,
        index:            Int
    ): LocalVariableTableEntry =
        new LocalVariableTableEntry(start_pc, length, name_index, descriptor_index, index)

    val LocalVariableTypeTableEntryManifest: ClassTag[LocalVariableTypeTableEntry] = implicitly

    type LocalVariableTypeTable_attribute = da.LocalVariableTypeTable_attribute
    def LocalVariableTypeTable_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        local_variable_type_table: LocalVariableTypes
    ): LocalVariableTypeTable_attribute =
        new LocalVariableTypeTable_attribute(
            attribute_name_index, local_variable_type_table
        )

    type LocalVariableTypeTableEntry = da.LocalVariableTypeTableEntry
    def LocalVariableTypeTableEntry(
        cp:              Constant_Pool,
        start_pc:        Int,
        length:          Int,
        name_index:      Constant_Pool_Index,
        signature_index: Constant_Pool_Index,
        index:           Int
    ): LocalVariableTypeTableEntry =
        new LocalVariableTypeTableEntry(
            start_pc, length, name_index, signature_index, index
        )

    val ElementValueManifest: ClassTag[ElementValue] = implicitly
    val ElementValuePairManifest: ClassTag[ElementValuePair] = implicitly

    type ElementValuePair = da.ElementValuePair
    type ElementValue = da.ElementValue
    def ElementValuePair(
        cp:                 Constant_Pool,
        element_name_index: Constant_Pool_Index, element_value: ElementValue
    ): ElementValuePair =
        new ElementValuePair(element_name_index, element_value)

    def ByteValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new ByteValue(const_value_index)

    def CharValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new CharValue(const_value_index)

    def DoubleValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new DoubleValue(const_value_index)

    def FloatValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new FloatValue(const_value_index)

    def IntValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new IntValue(const_value_index)

    def LongValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new LongValue(const_value_index)

    def ShortValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new ShortValue(const_value_index)

    def BooleanValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new BooleanValue(const_value_index)

    def StringValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new StringValue(const_value_index)

    def ClassValue(cp: Constant_Pool, const_value_index: Constant_Pool_Index): ElementValue =
        new ClassValue(const_value_index)

    def EnumValue(
        cp:               Constant_Pool,
        type_name_index:  Constant_Pool_Index,
        const_name_index: Constant_Pool_Index
    ): ElementValue =
        new EnumValue(type_name_index, const_name_index)

    type Annotation = da.Annotation
    def AnnotationValue(cp: Constant_Pool, annotation: Annotation): ElementValue =
        new AnnotationValue(annotation)

    def ArrayValue(cp: Constant_Pool, values: ElementValues): ElementValue =
        new ArrayValue(values)

    val AnnotationManifest: ClassTag[Annotation] = implicitly
    def Annotation(
        cp:                  Constant_Pool,
        type_index:          Constant_Pool_Index,
        element_value_pairs: ElementValuePairs
    ): Annotation =
        new Annotation(type_index, element_value_pairs)

    type AnnotationDefault_attribute = da.AnnotationDefault_attribute
    def AnnotationDefault_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        element_value: ElementValue
    ): AnnotationDefault_attribute =
        new AnnotationDefault_attribute(attribute_name_index, element_value)

    type RuntimeVisibleAnnotations_attribute = da.RuntimeVisibleAnnotations_attribute
    def RuntimeVisibleAnnotations_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        annotations: Annotations
    ): RuntimeVisibleAnnotations_attribute =
        new RuntimeVisibleAnnotations_attribute(attribute_name_index, annotations)

    type RuntimeInvisibleAnnotations_attribute = da.RuntimeInvisibleAnnotations_attribute
    def RuntimeInvisibleAnnotations_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        annotations: Annotations
    ): RuntimeInvisibleAnnotations_attribute =
        new RuntimeInvisibleAnnotations_attribute(attribute_name_index, annotations)

    type RuntimeVisibleParameterAnnotations_attribute = da.RuntimeVisibleParameterAnnotations_attribute
    def RuntimeVisibleParameterAnnotations_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        parameters_annotations: ParametersAnnotations
    ): RuntimeVisibleParameterAnnotations_attribute =
        new RuntimeVisibleParameterAnnotations_attribute(
            attribute_name_index, parameters_annotations
        )

    type RuntimeInvisibleParameterAnnotations_attribute = da.RuntimeInvisibleParameterAnnotations_attribute
    def RuntimeInvisibleParameterAnnotations_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        parameters_annotations: ParametersAnnotations
    ): RuntimeInvisibleParameterAnnotations_attribute =
        new RuntimeInvisibleParameterAnnotations_attribute(
            attribute_name_index, parameters_annotations
        )

    val StackMapFrameManifest: ClassTag[StackMapFrame] = implicitly

    type StackMapFrame = da.StackMapFrame
    type StackMapTable_attribute = da.StackMapTable_attribute
    def StackMapTable_attribute(
        cp: Constant_Pool, attribute_name_index: Constant_Pool_Index,
        stack_map_frames: StackMapFrames
    ): StackMapTable_attribute =
        new StackMapTable_attribute(attribute_name_index, stack_map_frames)

    def SameFrame(frame_type: Int): StackMapFrame = new SameFrame(frame_type)

    val VerificationTypeInfoManifest: ClassTag[VerificationTypeInfo] = implicitly
    type VerificationTypeInfo = da.VerificationTypeInfo

    def SameLocals1StackItemFrame(
        frame_type:                   Int,
        verification_type_info_stack: VerificationTypeInfo
    ): StackMapFrame =
        new SameLocals1StackItemFrame(frame_type, verification_type_info_stack)

    def ChopFrame(frame_type: Int, offset_delta: Int): StackMapFrame =
        new ChopFrame(frame_type, offset_delta)

    def SameFrameExtended(frame_type: Int, offset_delta: Int): StackMapFrame =
        new SameFrameExtended(frame_type, offset_delta)

    def AppendFrame(
        frame_type:                    Int,
        offset_delta:                  Int,
        verification_type_info_locals: VerificationTypeInfoLocals
    ): StackMapFrame =
        new AppendFrame(frame_type, offset_delta, verification_type_info_locals)

    def SameLocals1StackItemFrameExtended(
        frame_type:                   Int,
        offset_delta:                 Int,
        verification_type_info_stack: VerificationTypeInfo
    ): StackMapFrame =
        new SameLocals1StackItemFrameExtended(frame_type, offset_delta, verification_type_info_stack)

    def FullFrame(
        frame_type:                    Int,
        offset_delta:                  Int,
        verification_type_info_locals: VerificationTypeInfoLocals,
        verification_type_info_stack:  VerificationTypeInfoStack
    ): StackMapFrame =
        new FullFrame(frame_type, offset_delta, verification_type_info_locals, verification_type_info_stack)

    type TopVariableInfo = da.TopVariableInfo.type
    def TopVariableInfo(): VerificationTypeInfo = da.TopVariableInfo

    type IntegerVariableInfo = da.IntegerVariableInfo.type
    def IntegerVariableInfo(): VerificationTypeInfo = da.IntegerVariableInfo

    type FloatVariableInfo = da.FloatVariableInfo.type
    def FloatVariableInfo(): VerificationTypeInfo = da.FloatVariableInfo

    type LongVariableInfo = da.LongVariableInfo.type
    def LongVariableInfo(): VerificationTypeInfo = da.LongVariableInfo

    type DoubleVariableInfo = da.DoubleVariableInfo.type
    def DoubleVariableInfo(): VerificationTypeInfo = da.DoubleVariableInfo

    type NullVariableInfo = da.NullVariableInfo.type
    def NullVariableInfo(): VerificationTypeInfo = da.NullVariableInfo

    type UninitializedThisVariableInfo = da.UninitializedThisVariableInfo.type
    def UninitializedThisVariableInfo(): VerificationTypeInfo = da.UninitializedThisVariableInfo

    type UninitializedVariableInfo = da.UninitializedVariableInfo
    def UninitializedVariableInfo(offset: Int): VerificationTypeInfo =
        new UninitializedVariableInfo(offset)

    type ObjectVariableInfo = da.ObjectVariableInfo
    def ObjectVariableInfo(cp: Constant_Pool, cpool_index: Int): VerificationTypeInfo =
        new ObjectVariableInfo(cpool_index)

    val MethodParameterManifest: ClassTag[MethodParameter] = implicitly
    val TypeAnnotationManifest: ClassTag[TypeAnnotation] = implicitly

    type MethodParameters_attribute = da.MethodParameters_attribute
    def MethodParameters_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        parameters:           MethodParameters
    ): MethodParameters_attribute =
        new MethodParameters_attribute(attribute_name_index, parameters)

    type MethodParameter = da.MethodParameter
    def MethodParameter(
        cp:           Constant_Pool,
        name_index:   Constant_Pool_Index,
        access_flags: Int
    ): MethodParameter =
        new MethodParameter(name_index, access_flags)

    type TypeAnnotationTarget = da.TypeAnnotationTarget
    def ParameterDeclarationOfClassOrInterface(
        type_parameter_index: Int
    ): TypeAnnotationTarget =
        new ParameterDeclarationOfClassOrInterface(type_parameter_index)

    def ParameterDeclarationOfMethodOrConstructor(
        type_parameter_index: Constant_Pool_Index
    ): TypeAnnotationTarget =
        new ParameterDeclarationOfMethodOrConstructor(type_parameter_index)

    def SupertypeTarget(supertype_index: Int): TypeAnnotationTarget = {
        new Supertype_Target(supertype_index)
    }

    def TypeBoundOfParameterDeclarationOfClassOrInterface(
        type_parameter_index: Constant_Pool_Index,
        bound_index:          Constant_Pool_Index
    ): TypeAnnotationTarget = {
        new TypeBoundOfParameterDeclarationOfClassOrInterface(type_parameter_index, bound_index)
    }

    def TypeBoundOfParameterDeclarationOfMethodOrConstructor(
        type_parameter_index: Constant_Pool_Index,
        bound_index:          Constant_Pool_Index
    ): TypeAnnotationTarget = {
        new TypeBoundOfParameterDeclarationOfMethodOrConstructor(type_parameter_index, bound_index)
    }

    def FieldDeclaration: TypeAnnotationTarget = da.FieldDeclaration
    def ReturnType: TypeAnnotationTarget = da.ReturnType
    def ReceiverType: TypeAnnotationTarget = da.ReceiverType

    def FormalParameter(formal_parameter_index: Int): TypeAnnotationTarget = {
        new Formal_Parameter_Target(formal_parameter_index)
    }

    def Throws(throws_type_index: Constant_Pool_Index): TypeAnnotationTarget = {
        new Throws_Target(throws_type_index)
    }

    def Catch(exception_table_index: Int): TypeAnnotationTarget = {
        new Catch_Target(exception_table_index)
    }

    type LocalvarTableEntry = da.LocalvarTableEntry
    def LocalvarTableEntry(
        start_pc:                   Int,
        length:                     Int,
        local_variable_table_index: Int
    ): LocalvarTableEntry = {
        new LocalvarTableEntry(start_pc, length, local_variable_table_index)
    }

    def LocalvarDecl(localVarTable: LocalvarTable): TypeAnnotationTarget = {
        new LocalvarDecl(localVarTable)
    }

    def ResourcevarDecl(localVarTable: LocalvarTable): TypeAnnotationTarget = {
        new ResourcevarDecl(localVarTable)
    }

    def InstanceOf(offset: Int): TypeAnnotationTarget = new InstanceOf(offset)

    def New(offset: Int): TypeAnnotationTarget = new New(offset)

    def MethodReferenceExpressionNew /*::New*/ (offset: Int): TypeAnnotationTarget = {
        new MethodReferenceExpressionNew(offset)
    }

    def MethodReferenceExpressionIdentifier /*::Identifier*/ (offset: Int): TypeAnnotationTarget = {
        new MethodReferenceExpressionIdentifier(offset)
    }

    def CastExpression(offset: Int, type_argument_index: Int): TypeAnnotationTarget = {
        new CastExpression(offset, type_argument_index)
    }

    def ConstructorInvocation(offset: Int, type_argument_index: Int): TypeAnnotationTarget = {
        new ConstructorInvocation(offset, type_argument_index)
    }

    def MethodInvocation(offset: Int, type_argument_index: Int): TypeAnnotationTarget = {
        new MethodInvocation(offset, type_argument_index)
    }

    def ConstructorInMethodReferenceExpression(
        offset:              Int,
        type_argument_index: Int
    ): TypeAnnotationTarget = {
        new ConstructorInMethodReferenceExpression(offset, type_argument_index)
    }

    def MethodInMethodReferenceExpression(
        offset:              Int,
        type_argument_index: Int
    ): TypeAnnotationTarget = {
        new MethodInMethodReferenceExpression(offset, type_argument_index)
    }

    type TypeAnnotationPath = da.TypeAnnotationPath
    def TypeAnnotationDirectlyOnType = da.TypeAnnotationDirectlyOnType

    type TypeAnnotationPathElement = da.TypeAnnotationPathElement
    def TypeAnnotationPath(path: IndexedSeq[TypeAnnotationPathElement]): TypeAnnotationPath = {
        new TypeAnnotationPathElements(path)
    }

    /**
     * The `type_path_kind` was `0` (and the type_argument_index was also `0`).
     */
    def TypeAnnotationDeeperInArrayType = da.TypeAnnotationDeeperInArrayType

    /**
     * The `type_path_kind` was `1` (and the type_argument_index was (as defined by the
     * specification) also `0`).
     */
    def TypeAnnotationDeeperInNestedType = da.TypeAnnotationDeeperInNestedType

    /**
     * The `type_path_kind` was `2` (and the type_argument_index was (as defined by the
     * specification) also `0`).
     */
    def TypeAnnotationOnBoundOfWildcardType = da.TypeAnnotationOnBoundOfWildcardType

    def TypeAnnotationOnTypeArgument(type_argument_index: Int): TypeAnnotationPathElement = {
        new TypeAnnotationOnTypeArgument(type_argument_index)
    }

    type TypeAnnotation = da.TypeAnnotation
    def TypeAnnotation(
        cp:                  Constant_Pool,
        target:              TypeAnnotationTarget,
        path:                TypeAnnotationPath,
        type_index:          Constant_Pool_Index,
        element_value_pairs: ElementValuePairs
    ): TypeAnnotation = {
        new TypeAnnotation(target, path, type_index, element_value_pairs)
    }

    type RuntimeInvisibleTypeAnnotations_attribute = da.RuntimeInvisibleTypeAnnotations_attribute
    def RuntimeInvisibleTypeAnnotations_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        annotations:          TypeAnnotations
    ): RuntimeInvisibleTypeAnnotations_attribute = {
        new RuntimeInvisibleTypeAnnotations_attribute(attribute_name_index, annotations)
    }

    type RuntimeVisibleTypeAnnotations_attribute = da.RuntimeVisibleTypeAnnotations_attribute
    def RuntimeVisibleTypeAnnotations_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        annotations:          TypeAnnotations
    ): RuntimeVisibleTypeAnnotations_attribute = {
        new RuntimeVisibleTypeAnnotations_attribute(attribute_name_index, annotations)
    }

    // --------------------------------------------------------------------------------------------
    // JAVA 9
    // --------------------------------------------------------------------------------------------

    type ConcealedPackagesEntry = Constant_Pool_Index
    implicit val ConcealedPackagesEntryManifest: ClassTag[ConcealedPackagesEntry] = implicitly
    def ConcealedPackagesEntry(
        cp:            Constant_Pool,
        package_index: Constant_Pool_Index
    ): ConcealedPackagesEntry = package_index

    type ConcealedPackages_attribute = da.ConcealedPackages_attribute
    def ConcealedPackages_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        packages:             ConcealedPackages
    ): ConcealedPackages_attribute = {
        new ConcealedPackages_attribute(attribute_name_index, packages)
    }

    type MainClass_attribute = da.MainClass_attribute
    def MainClass_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        main_class_index:     Constant_Pool_Index
    ): MainClass_attribute = {
        new MainClass_attribute(attribute_name_index, main_class_index)
    }

    type TargetPlatform_attribute = da.TargetPlatform_attribute
    def TargetPlatform_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        os_name_index:        Constant_Pool_Index,
        os_arch_index:        Constant_Pool_Index,
        os_version_index:     Constant_Pool_Index
    ): TargetPlatform_attribute = {
        new TargetPlatform_attribute(
            attribute_name_index,
            os_name_index, os_arch_index, os_version_index
        )
    }

    type Version_attribute = da.Version_attribute
    def Version_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        version_index:        Constant_Pool_Index
    ): Version_attribute = {
        new Version_attribute(attribute_name_index, version_index)
    }

    type Module_attribute = da.Module_attribute

    type RequiresEntry = da.RequiresEntry
    implicit val RequiresEntryManifest: ClassTag[RequiresEntry] = implicitly

    type ExportsEntry = da.ExportsEntry
    implicit val ExportsEntryManifest: ClassTag[ExportsEntry] = implicitly

    type ExportsToEntry = da.ExportsToEntry
    implicit val ExportsToEntryManifest: ClassTag[ExportsToEntry] = implicitly

    type UsesEntry = da.UsesEntry
    implicit val UsesEntryManifest: ClassTag[ClassFileReader.UsesEntry] = implicitly

    type ProvidesEntry = da.ProvidesEntry
    implicit val ProvidesEntryManifest: ClassTag[ProvidesEntry] = implicitly

    def Module_attribute(
        constant_pool:        Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        requires:             Requires,
        exports:              Exports,
        uses:                 Uses,
        provides:             Provides
    ) = new Module_attribute(attribute_name_index, requires, exports, uses, provides)

    def RequiresEntry(
        constant_pool:  Constant_Pool,
        requires_index: Constant_Pool_Index, // CONSTANT_UTF8
        requires_flags: Int
    ): RequiresEntry = new RequiresEntry(requires_index, requires_flags)

    def ExportsEntry(
        constant_pool: Constant_Pool,
        exports_index: Constant_Pool_Index, // CONSTANT_UTF8
        // TODO Documented in JSR by not yet(?) generated by the JDK 9 javac (Aug. 2016):exports_flags: Int,
        exports_to: ExportsTo
    ): ExportsEntry = {
        new ExportsEntry(
            exports_index,
            // TODO Documented in JSR by not yet(?) generated by the JDK 9 javac (Aug. 2016): exports_flags, 
            exports_to
        )
    }

    def ExportsToEntry(
        constant_pool:    Constant_Pool,
        exports_to_index: Constant_Pool_Index // CONSTANT_UTF8
    ): ExportsToEntry = new ExportsToEntry(exports_to_index)

    def UsesEntry(
        constant_pool: Constant_Pool,
        uses_index:    Constant_Pool_Index // CONSTANT_Class
    ): UsesEntry = new UsesEntry(uses_index)

    def ProvidesEntry(
        constant_pool:  Constant_Pool,
        provides_index: Constant_Pool_Index, // CONSTANT_Class
        with_index:     Constant_Pool_Index
    ): ProvidesEntry = new ProvidesEntry(provides_index, with_index)

}
