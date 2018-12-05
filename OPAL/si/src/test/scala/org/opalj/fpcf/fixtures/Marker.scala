/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf
package fixtures

/**
 * Models a basic property to "mark" something.
 *
 * @note Only intended to be used as a test fixture.
 */
object Marker {

    final val MarkerKey = {
        PropertyKey.create[Entity, MarkerProperty](
            "Marker",
            (ps: PropertyStore, reason: FallbackReason, e: Entity) ⇒ NotMarked,
            // The fast-track property computation function is deliberately defined as
            // always "failing".
            (ps: PropertyStore, e: Entity) ⇒ None
        )
    }

    sealed trait MarkerProperty extends Property {
        type Self = MarkerProperty
        def key: PropertyKey[MarkerProperty] = MarkerKey
    }

    case object IsMarked extends MarkerProperty
    case object NotMarked extends MarkerProperty
}
