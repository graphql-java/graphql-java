package graphql.execution.directives

import com.google.common.collect.ImmutableList
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLScalarType
import graphql.util.FpKit
import spock.lang.Specification

class OperationDirectivesResolverTest extends Specification {

    def schema = TestUtil.schema("""
            directive @foo on QUERY | MUTATION | SUBSCRIPTION
            directive @bar on QUERY | MUTATION | SUBSCRIPTION
            directive @baz repeatable on QUERY | MUTATION | SUBSCRIPTION
            directive @timeout(ms : Int = -1) on QUERY | MUTATION | SUBSCRIPTION

            type Query {
                f : String
            } 
            type Mutation {
                f : String
            } 
            type Subscription {
                f : String
            } 
        """)

    def "can resolve out directives on a document"() {


        def document = TestUtil.parseQuery("""
            query q1 @foo {
                f
            }

            query q2 @bar {
                f
            }

            mutation m1 @baz @baz {
                f
            }
            
            subscription s1 @timeout(ms : 100) {
                f
            }            
           
        """)

        when:
        def resolveDirectives = new OperationDirectivesResolver()
                .resolveDirectives(document, schema, CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.getDefault())

        def data = resolveDirectives.collectEntries { operation, directives ->
            [operation.name, directives.collect { it.name }] // remap to names
        }
        then:
        !resolveDirectives.isEmpty()
        data["q1"] == ["foo"]
        data["q2"] == ["bar"]
        data["m1"] == ["baz", "baz"]
        data["s1"] == ["timeout"]
    }

    def "can resolve out directives on an operation"() {

        def document = TestUtil.parseQuery("""
            query q1 @timeout(ms : 100) @foo @bar {
                f
            }
        """)

        def operationDefinition = extractOp(document)

        when:
        def resolveDirectives = new OperationDirectivesResolver()
                .resolveDirectivesByName(operationDefinition, schema, CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.getDefault())

        then:
        resolveDirectives.size() == 3
        def directives = resolveDirectives["timeout"]
        directives.size() == 1

        timeoutAsserts(directives[0], 100)
    }

    def "can default values in directives"() {
        def document = TestUtil.parseQuery("""
            query q1 @timeout @foo @bar {
                f
            }
        """)
        def operationDefinition = extractOp(document)

        when:
        def resolveDirectives = new OperationDirectivesResolver()
                .resolveDirectivesByName(operationDefinition, schema, CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.getDefault())

        then:
        resolveDirectives.size() == 3
        def directives = resolveDirectives["timeout"]
        directives.size() == 1

        timeoutAsserts(directives[0], -1)

    }


    private static boolean timeoutAsserts(QueryAppliedDirective directive, Integer value) {
        assert directive.name == "timeout"
        assert directive.arguments.size() == 1
        assert directive.arguments[0].name == "ms"
        assert (directive.arguments[0].type as GraphQLScalarType).name == "Int"
        assert directive.arguments[0].value == value
        true
    }

    def "integration test"() {

        ExecutionContext executionContext = null
        Instrumentation instrumentation = new Instrumentation() {
            @Override
            InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
                executionContext = parameters.getExecutionContext()
                return null
            }
        }

        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()

        when:
        def ei = ExecutionInput.newExecutionInput("""
            query q1 @timeout(ms : 100) @foo @bar @baz @baz {
                f
            }
            
            mutation m1 @timeout(ms : 100) @foo @bar @baz @baz {
                f
            }
        """).operationName("q1").build()
        graphQL.execute(ei)


        then:
        def resolveDirectives = executionContext.getOperationDirectives()

        commonIntegrationAsserts(resolveDirectives)

        when:
        def normalizedOperation = executionContext.getNormalizedQueryTree().get()
        def enoResolveDirectives = normalizedOperation.getOperationDirectives()

        then:
        commonIntegrationAsserts(enoResolveDirectives)

        when:
        def allOperationDirectives = executionContext.getAllOperationDirectives()

        then:
        allOperationDirectives.size() == 2
        ImmutableList<QueryAppliedDirective> firstList = allOperationDirectives.values().iterator().next()
        def firstResolvedDirectives = FpKit.groupingBy(firstList, { it -> it.name })
        commonIntegrationAsserts(firstResolvedDirectives)

    }

    private static boolean commonIntegrationAsserts(Map<String, ImmutableList<QueryAppliedDirective>> resolveDirectives) {
        assert resolveDirectives.size() == 4
        def directives = resolveDirectives["timeout"]
        assert directives.size() == 1

        def directive = directives[0]
        assert directive.name == "timeout"
        assert directive.arguments.size() == 1
        assert directive.arguments[0].name == "ms"
        assert (directive.arguments[0].type as GraphQLScalarType).name == "Int"
        assert directive.arguments[0].value == 100

        assert  resolveDirectives["foo"].size() == 1
        assert  resolveDirectives["bar"].size() == 1
        assert  resolveDirectives["baz"].size() == 2

        true

    }

    private static OperationDefinition extractOp(Document document) {
        document.getDefinitionsOfType(OperationDefinition.class)[0]
    }

}
