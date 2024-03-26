package graphql.execution

import graphql.AssertException
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.execution.FieldValueInfo.CompleteValueType.SCALAR


class FieldValueInfoTest extends Specification {

    def "simple constructor test"() {
        when:
        def fieldValueInfo = new FieldValueInfo(SCALAR, CompletableFuture.completedFuture("A"))

        then: "fieldValueInfos to be empty list"
        fieldValueInfo.fieldValueInfos == [] as List
        fieldValueInfo.fieldValueFuture.join() == "A"
        fieldValueInfo.completeValueType == SCALAR
    }

    def "negative constructor test"() {
        when:
        new FieldValueInfo(SCALAR, CompletableFuture.completedFuture("A"), null)
        then:
        def assEx = thrown(AssertException)
        assEx.message.contains("fieldValueInfos")
    }
}
