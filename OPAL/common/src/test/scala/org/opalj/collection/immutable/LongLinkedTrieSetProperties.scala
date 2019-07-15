/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.collection
package immutable

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
object LongLinkedTrieSetProperties extends LongSetProperties("LongLinkedTrieSetProperties") {

    def empty(): LongSet = LongLinkedTrieSet.empty

}
