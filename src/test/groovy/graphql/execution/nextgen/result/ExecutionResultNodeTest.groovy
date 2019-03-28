package graphql.execution.nextgen.result

import graphql.Scalars
import graphql.execution.nextgen.FetchedValueAnalysis
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.GraphqlErrorBuilder.newError
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.FetchedValue.newFetchedValue
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR
import static graphql.execution.nextgen.FetchedValueAnalysis.newFetchedValueAnalysis

class ExecutionResultNodeTest extends Specification {

    @Shared
    def startingErrors = [newError().message("Starting").build()]

    @Shared
    def startingFetchValueAnalysis = fetchedValueAnalysis("Starting")

    @Shared
    def startingChildren = [new LeafExecutionResultNode(startingFetchValueAnalysis, null)]

    static FetchedValueAnalysis fetchedValueAnalysis(String value) {
        def executionStepInfo = newExecutionStepInfo().type(Scalars.GraphQLString).build()
        def fetchedValue = newFetchedValue()
                .fetchedValue(value)
                .rawFetchedValue(value).build()
        newFetchedValueAnalysis()
                .executionStepInfo(executionStepInfo)
                .valueType(SCALAR)
                .fetchedValue(fetchedValue)
                .completedValue(value)
                .build()
    }

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
        def newFetchValueAnalysis = fetchedValueAnalysis("hi")

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
}
