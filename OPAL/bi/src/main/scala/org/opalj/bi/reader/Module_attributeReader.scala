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
package bi
package reader

import scala.reflect.ClassTag

import java.io.DataInputStream
import org.opalj.control.repeat

/**
 * Generic parser for the ''Module'' attribute (Java 9).
 *
 * @note This implementation is based on:
 *      http://cr.openjdk.java.net/~mr/jigsaw/spec/lang-vm.html
 *      August 2016
 *
 * @author Michael Eichberg
 */
trait Module_attributeReader extends AttributeReader {

    type Module_attribute <: Attribute

    type RequiresEntry
    implicit val RequiresEntryManifest: ClassTag[RequiresEntry]

    type ExportsEntry
    implicit val ExportsEntryManifest: ClassTag[ExportsEntry]

    type ExportsToEntry
    implicit val ExportsToEntryManifest: ClassTag[ExportsToEntry]

    type UsesEntry
    implicit val UsesEntryManifest: ClassTag[UsesEntry]

    type ProvidesEntry
    implicit val ProvidesEntryManifest: ClassTag[ProvidesEntry]

    def Module_attribute(
        constant_pool:        Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        requires:             Requires,
        exports:              Exports,
        uses:                 Uses,
        provides:             Provides
    ): Module_attribute

    def RequiresEntry(
        constant_pool:  Constant_Pool,
        requires_index: Constant_Pool_Index, // CONSTANT_UTF8
        requires_flags: Int
    ): RequiresEntry

    def ExportsEntry(
        constant_pool: Constant_Pool,
        exports_index: Constant_Pool_Index, // CONSTANT_UTF8
        // TODO Documented in JSR by not yet(?) generated by the JDK 9 javac (Aug. 2016): exportsFlags:  Int,
        exportsTo: ExportsTo
    ): ExportsEntry

    def ExportsToEntry(
        constant_pool:    Constant_Pool,
        exports_to_index: Constant_Pool_Index // CONSTANT_UTF8
    ): ExportsToEntry

    def UsesEntry(
        constant_pool: Constant_Pool,
        uses_index:    Constant_Pool_Index // CONSTANT_Class
    ): UsesEntry

    def ProvidesEntry(
        constant_pool:  Constant_Pool,
        provides_index: Constant_Pool_Index, // CONSTANT_Class
        with_index:     Constant_Pool_Index
    ): ProvidesEntry

    //
    // IMPLEMENTATION
    //

    type Requires = IndexedSeq[RequiresEntry]
    type Exports = IndexedSeq[ExportsEntry]
    type ExportsTo = IndexedSeq[ExportsToEntry]
    type Uses = IndexedSeq[UsesEntry]
    type Provides = IndexedSeq[ProvidesEntry]

    /**
     * Parser for the Java 9 Module attribute.
     *
     * Structure:
     * <pre>
     * Module_attribute {
     *     u2 attribute_name_index;
     *     u4 attribute_length;
     *
     *     u2 requires_count;
     *     {   u2 requires_index; // CONSTANT_Utf8
     *         u2 requires_flags;
     *     } requires[requires_count];
     *
     *     u2 exports_count;
     *     {   u2 exports_index; // CONSTANT_Utf8
     *         // TODO Documented in JSR by not yet(?) generated by the JDK 9 javac (Aug. 2016): u2 exports_flags;
     *         u2 exports_to_count;
     *         u2 exports_to_index/*CONSTANT_UTF8*/[exports_to_count];
     *     } exports[exports_count];
     *
     *     u2 uses_count;
     *     u2 uses_index/*CONSTANT_Class*/[uses_count];
     *
     *     u2 provides_count;
     *     {   u2 provides_index /*CONSTANT_Class*/;
     *         u2 with_index /*CONSTANT_Class*/;
     *     } provides[provides_count];
     * }
     * </pre>
     */
    private[this] def parser(
        ap:                   AttributeParent,
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        in:                   DataInputStream
    ): Attribute = {
        /*val attribute_length = */ in.readInt()

        val requiresCount = in.readUnsignedShort()
        val requires = repeat(requiresCount) {
            RequiresEntry(cp, in.readUnsignedShort(), in.readUnsignedShort())
        }

        val exportsCount = in.readUnsignedShort()
        val exports = repeat(exportsCount) {
            ExportsEntry(
                cp,
                in.readUnsignedShort(),
                // TODO Documented in JSR by not yet(?) generated by the JDK 9 javac (Aug. 2016): in.readUnsignedShort()  
                {
                    val exportsToCount = in.readUnsignedShort()
                    repeat(exportsToCount) {
                        val cpIndex = in.readUnsignedShort()
                        ExportsToEntry(cp, cpIndex)
                    }
                }
            )
        }

        val usesCount = in.readUnsignedShort()
        val uses = repeat(usesCount) {
            UsesEntry(cp, in.readUnsignedShort())
        }

        val providesCount = in.readUnsignedShort()
        val provides = repeat(providesCount) {
            ProvidesEntry(cp, in.readUnsignedShort(), in.readUnsignedShort())
        }

        if (reifyEmptyAttributes ||
            requiresCount > 0 ||
            exportsCount > 0 ||
            usesCount > 0 ||
            providesCount > 0) {
            Module_attribute(cp, attribute_name_index, requires, exports, uses, provides)
        } else {
            null
        }
    }

    registerAttributeReader(ModuleAttribute.Name → parser)
}

object ModuleAttribute {

    final val Name = "Module"

}

