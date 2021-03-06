/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package collection

/**
 * Common interface of all BitSets provided by OPAL.
 *
 * @author Michael Eichberg
 */
trait BitSet { thisSet ⇒

    def isEmpty: Boolean

    def contains(i: Int): Boolean

    def iterator: IntIterator

    final def mkString(pre: String, in: String, post: String): String = {
        iterator.mkString(pre, in, post)
    }

    // + equals and hashCode
}
