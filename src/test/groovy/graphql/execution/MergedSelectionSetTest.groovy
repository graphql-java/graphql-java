package graphql.execution

import graphql.TestUtil
import spock.lang.Specification

class MergedSelectionSetTest extends Specification {

    def "returns sub fields as a list in insertion order"() {
        given:
        def first = TestUtil.mergedField(TestUtil.parseField("first"))
        def second = TestUtil.mergedField(TestUtil.parseField("second"))
        def subFields = new LinkedHashMap<String, MergedField>()
        subFields.put("first", first)
        subFields.put("second", second)

        when:
        def selectionSet = MergedSelectionSet.newMergedSelectionSet()
                .subFields(subFields)
                .build()

        then:
        selectionSet.subFieldsList == [first, second]
        !selectionSet.empty
    }
}
