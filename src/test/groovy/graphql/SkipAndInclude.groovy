package graphql

import graphql.execution.CoercedVariables
import graphql.execution.NonNullableValueCoercedAsNullException
import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.ReactiveStreamsObjectPublisher
import graphql.language.SourceLocation
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.parser.Parser
import graphql.schema.DataFetcher
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SkipAndInclude extends Specification {

    private def graphQL = TestUtil.graphQL("""
            type Query {
                field: Int
            }
        """).build()

    def "@skip and @include"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkipAndInclude($skip: Boolean!, $include: Boolean!) {
                        field @skip(if: $skip) @include(if: $include)
                    }   
                    ''')
                .variables([skip: skip, include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | include | queried
        true  | true    | false
        true  | false   | false
        false | true    | true
        false | false   | false

    }

    def "@skip"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkip($skip: Boolean!) {
                        field @skip(if: $skip)
                    }   
                    ''')
                .variables([skip: skip])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | queried
        true  | false
        false | true
    }

    def "@include"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithInclude($include: Boolean!) {
                        field @include(if: $include)
                    }   
                    ''')
                .variables([include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        include | queried
        true    | true
        false   | false
    }

    private static GraphQL nestedGraphQL() {
        def item = [child: "child"]
        def parentFetcher = { env -> item } as DataFetcher
        def asyncParentFetcher = { env -> CompletableFuture.supplyAsync({ item }) } as DataFetcher
        def eventsFetcher = { env -> new ReactiveStreamsObjectPublisher(2, { i -> item }) } as DataFetcher
        def schema = TestUtil.schema("""
            type Query {
                ok: String
                parent: Item
                asyncParent: Item
                wrapper: Wrapper
                items: [Item]
            }
            type Mutation {
                ok: String
                parent: Item
            }
            type Subscription {
                event: Item
            }
            type Wrapper {
                inner: Item!
            }
            type Item {
                child: String
            }
        """, [
                Query       : [ok: { env -> "ok" } as DataFetcher, parent: parentFetcher, asyncParent: asyncParentFetcher,
                               wrapper: { env -> [inner: item] } as DataFetcher, items: { env -> [item, item] } as DataFetcher],
                Mutation    : [ok: { env -> "ok" } as DataFetcher, parent: parentFetcher],
                Subscription: [event: eventsFetcher]
        ])
        GraphQL.newGraphQL(schema).build()
    }

    private static ExecutionInput nullFlagInput(String query) {
        ExecutionInput.newExecutionInput().query(query).variables([flag: null]).build()
    }

    private static String coercedNullMessage(String directive) {
        "Variable 'flag' has coerced Null value for the non-null 'if' argument of directive '@$directive'"
    }

    def "null value for a root #directive if argument is a coercion error with no data"() {
        // a nullable variable with a non null default value is a legal usage in the non null 'if' position,
        // so an explicit null variable value reaches the directive and must be reported as a coercion error
        when:
        def executionResult = graphQL.execute(nullFlagInput("""query(\$flag: Boolean = true) {
  field @$directive(if: \$flag)
}"""))

        then:
        !executionResult.isDataPresent()
        !executionResult.toSpecification().containsKey("data")
        executionResult.errors.size() == 1
        executionResult.errors[0] instanceof NonNullableValueCoercedAsNullException
        executionResult.errors[0].message == coercedNullMessage(directive)
        executionResult.errors[0].errorType == ErrorType.ValidationError
        executionResult.errors[0].locations == [new SourceLocation(2, 15 + directive.length())]
        executionResult.errors[0].path == null

        where:
        directive << ["skip", "include"]
    }

    def "null value for a nested #directive if argument nulls the nullable parent of #operation and keeps sibling data"() {
        when:
        def executionResult = nestedGraphQL().execute(nullFlagInput("""$operation(\$flag: Boolean = true) {
  ok
  parent { child @$directive(if: \$flag) }
}"""))

        then:
        executionResult.toSpecification().data == [ok: "ok", parent: null]
        executionResult.errors.size() == 1
        executionResult.errors[0].message == coercedNullMessage(directive)
        executionResult.errors[0].errorType == ErrorType.ValidationError
        executionResult.errors[0].path == ["parent"]
        executionResult.errors[0].locations == [new SourceLocation(3, 24 + directive.length())]

        where:
        directive | operation
        "skip"    | "query"
        "include" | "query"
        "skip"    | "mutation"
        "include" | "mutation"
    }

    def "null value for a nested #directive if argument under an async parent nulls that parent"() {
        when:
        def executionResult = nestedGraphQL().execute(nullFlagInput("""query(\$flag: Boolean = true) {
  ok
  asyncParent { child @$directive(if: \$flag) }
}"""))

        then:
        executionResult.toSpecification().data == [ok: "ok", asyncParent: null]
        executionResult.errors.size() == 1
        executionResult.errors[0].message == coercedNullMessage(directive)
        executionResult.errors[0].path == ["asyncParent"]

        where:
        directive << ["skip", "include"]
    }

    def "null value for a nested #directive if argument under a non-null parent propagates to the nullable grandparent"() {
        when:
        def executionResult = nestedGraphQL().execute(nullFlagInput("""query(\$flag: Boolean = true) {
  ok
  wrapper { inner { child @$directive(if: \$flag) } }
}"""))

        then:
        executionResult.toSpecification().data == [ok: "ok", wrapper: null]
        executionResult.errors.size() == 1
        executionResult.errors[0].message == coercedNullMessage(directive)
        executionResult.errors[0].path == ["wrapper", "inner"]
        executionResult.errors[0].locations == [new SourceLocation(3, 33 + directive.length())]

        where:
        directive << ["skip", "include"]
    }

    def "null value for a nested #directive if argument in a list nulls each list item"() {
        when:
        def executionResult = nestedGraphQL().execute(nullFlagInput("""query(\$flag: Boolean = true) {
  ok
  items { child @$directive(if: \$flag) }
}"""))

        then:
        executionResult.toSpecification().data == [ok: "ok", items: [null, null]]
        executionResult.errors*.path == [["items", 0], ["items", 1]]
        executionResult.errors.every { it.message == coercedNullMessage(directive) }

        where:
        directive << ["skip", "include"]
    }

    def "null value for a nested #directive if argument in a subscription is an error on each event, not a stream failure"() {
        when:
        def executionResult = nestedGraphQL().execute(nullFlagInput("""subscription(\$flag: Boolean = true) {
  event { child @$directive(if: \$flag) }
}"""))
        Publisher<ExecutionResult> publisher = executionResult.getData()
        def subscriber = new CapturingSubscriber<ExecutionResult>()
        publisher.subscribe(subscriber)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilTrue(subscriber.isDone())

        then:
        subscriber.throwable == null
        subscriber.events.size() == 2
        subscriber.events.every { er ->
            er.toSpecification().data == [event: null] &&
                    er.errors.size() == 1 &&
                    er.errors[0].message == coercedNullMessage(directive) &&
                    er.errors[0].path == ["event"]
        }

        where:
        directive << ["skip", "include"]
    }

    def "null value for a #directive if argument is a coercion error when building a normalized operation"() {
        given:
        def schema = TestUtil.schema("type Query { field: Int }")
        def document = new Parser().parseDocument("""query(\$flag: Boolean = true) {
  field @$directive(if: \$flag)
}""")

        when:
        ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(
                schema, document, null, CoercedVariables.of([flag: null]))

        then:
        def e = thrown(NonNullableValueCoercedAsNullException)
        e.message == coercedNullMessage(directive)

        where:
        directive << ["skip", "include"]
    }
}
