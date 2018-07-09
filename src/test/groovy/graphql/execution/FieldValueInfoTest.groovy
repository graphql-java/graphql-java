package graphql.execution

import graphql.AssertException
import spock.lang.Specification


class FieldValueInfoTest extends Specification{

    def "simple constructor test"() {
        when:
        def fieldValueInfo = FieldValueInfo.newFieldValueInfo().build()

        then: "fieldValueInfos to be empty list"
        fieldValueInfo.fieldValueInfos == [] as List

        and: "other fields to be null "
        fieldValueInfo.fieldValue == null
        fieldValueInfo.completeValueType == null
    }

    def "negative constructor test"() {
        when:
        FieldValueInfo.newFieldValueInfo()
                .fieldValueInfos(null)
                .build()
        then:
        def assEx = thrown(AssertException)
        assEx.message.contains("fieldValueInfos")
    }
}
