package graphql.execution.nextgen.result

import graphql.Scalars
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.GraphqlErrorBuilder.newError
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo

class ExecutionResultNodeTest extends Specification {

    @Shared
    def startingErrors = [newError().message("Starting").build()]

    @Shared
    def startingExecutionStepInfo = newExecutionStepInfo().type(Scalars.GraphQLString).build()
    @Shared
    def startingResolveValue = ResolvedValue.newResolvedValue().completedValue("start").build();

    @Shared
    def startingChildren = [new LeafExecutionResultNode(startingExecutionStepInfo, startingResolveValue, null)]

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

        node                                                                                                             | _
        new RootExecutionResultNode([], startingErrors)                                                                  | _
        new ObjectExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors)   | _
        new LeafExecutionResultNode(startingExecutionStepInfo, startingResolveValue, null, startingErrors)               | _
    }

    @Unroll
    def "construction of objects with new esi works"() {

        given:
        def newEsi = newExecutionStepInfo().type(Scalars.GraphQLString).build()

        expect:
        ExecutionResultNode nodeUnderTest = node
        def newNode = nodeUnderTest.withNewExecutionStepInfo(newEsi)
        newNode != nodeUnderTest
        newNode.getExecutionStepInfo() == newEsi


        where:

        node                                                                                                             | _
        new ObjectExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors)   | _
        new LeafExecutionResultNode(startingExecutionStepInfo, startingResolveValue, null, startingErrors)               | _
    }

    @Unroll
    def "construction of objects with new children works"() {

        given:
        def newChildren = [
                new LeafExecutionResultNode(startingExecutionStepInfo, startingResolveValue, null),
                new LeafExecutionResultNode(startingExecutionStepInfo, startingResolveValue, null),
        ]

        expect:
        ExecutionResultNode nodeUnderTest = node
        node.getChildren().size() == 1
        def newNode = nodeUnderTest.withNewChildren(newChildren)
        newNode != nodeUnderTest
        newNode.getChildren().size() == 2


        where:

        node                                                                                                             | _
        new RootExecutionResultNode(startingChildren, startingErrors)                                                    | _
        new ObjectExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors) | _
        new ListExecutionResultNode(startingExecutionStepInfo, startingResolveValue, startingChildren, startingErrors)   | _
    }
}
