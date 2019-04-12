package graphql.execution.nextgen.result


import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.GraphqlErrorBuilder.newError
import static graphql.execution.nextgen.result.ExecutionResultNodeTestUtils.fvaForValue

class ExecutionResultNodeTest extends Specification {

    @Shared
    def startingErrors = [newError().message("Starting").build()]

    @Shared
    def startingFetchValueAnalysis = fvaForValue("Starting")

    @Shared
    def startingChildren = [new LeafExecutionResultNode(startingFetchValueAnalysis, null)]

    @Unroll
    def "construction of objects with new errors works"() {

        given:
        def expectedErrors = [newError().message("Expected").build()]

        expect:
        ExecutionResultNode nodeUnderTest = node
        def newNode = nodeUnderTest.withNewErrors(expectedErrors)
        newNode != nodeUnderTest
        newNode.getErrors() == expectedErrors

        where:

        node                                                                                        | _
        new RootExecutionResultNode([], startingErrors)                                             | _
        new ObjectExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors)   | _
        new LeafExecutionResultNode(startingFetchValueAnalysis, null, startingErrors)               | _
    }

    @Unroll
    def "construction of objects with new fetched value analysis  works"() {

        given:
        def newFetchValueAnalysis = fvaForValue("hi")

        expect:
        ExecutionResultNode nodeUnderTest = node
        def newNode = nodeUnderTest.withNewFetchedValueAnalysis(newFetchValueAnalysis)
        newNode != nodeUnderTest
        newNode.getFetchedValueAnalysis() != startingFetchValueAnalysis
        newNode.getFetchedValueAnalysis().getCompletedValue() == "hi"


        where:

        node                                                                                        | _
        new ObjectExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors)   | _
        new LeafExecutionResultNode(startingFetchValueAnalysis, null, startingErrors)               | _
    }

    @Unroll
    def "construction of objects with new children works"() {

        given:
        def newChildren = [
                new LeafExecutionResultNode(startingFetchValueAnalysis, null),
                new LeafExecutionResultNode(startingFetchValueAnalysis, null),
        ]

        expect:
        ExecutionResultNode nodeUnderTest = node
        node.getChildren().size() == 1
        def newNode = nodeUnderTest.withNewChildren(newChildren)
        newNode != nodeUnderTest
        newNode.getChildren().size() == 2


        where:

        node                                                                                        | _
        new RootExecutionResultNode(startingChildren, startingErrors)                               | _
        new ObjectExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors)   | _
    }


    @Unroll
    def "construction of objects with new context works"() {

        expect:
        ExecutionResultNode nodeUnderTest = node
        def className = node.getClass().getSimpleName()
        node.getContext() == null
        def newNode = nodeUnderTest.withNewContext(className)
        newNode != nodeUnderTest
        newNode.getContext() == className

        where:

        node                                                                                        | expectedValue
        new RootExecutionResultNode(startingChildren, startingErrors)                               | _
        new ObjectExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingFetchValueAnalysis, startingChildren, startingErrors)   | _
        new LeafExecutionResultNode(startingFetchValueAnalysis, null, [])                           | _
    }
}
