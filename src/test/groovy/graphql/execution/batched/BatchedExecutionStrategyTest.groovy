/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package graphql.execution.batched

import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.Scalars
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.NonNullableFieldWasNullError
import graphql.execution.instrumentation.TestingInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject

class BatchedExecutionStrategyTest extends Specification {

    private GraphQLSchema schema = new FunWithStringsSchemaFactory().createSchema()

    private GraphQL graphQLAsync = GraphQL.newGraphQL(schema)
            .queryExecutionStrategy(new AsyncExecutionStrategy())
            .build()

    private GraphQL graphQLBatchedButUnbatched = GraphQL.newGraphQL(this.schema)
            .queryExecutionStrategy(new BatchedExecutionStrategy())
            .build()

    private Map<FunWithStringsSchemaFactory.CallType, AtomicInteger> countMap = new HashMap<>()
    private TestingInstrumentation testingInstrumentation = new TestingInstrumentation()

    private GraphQL graphQLBatchedValue = GraphQL.newGraphQL(FunWithStringsSchemaFactory.createBatched(countMap).createSchema())
            .instrumentation(testingInstrumentation)
            .queryExecutionStrategy(new BatchedExecutionStrategy())
            .build()

    private void runTestExpectErrors(String query, Exception exception) {
        runTestAsyncExpectErrors(query, exception)
        runTestBatchingUnbatchedExpectErrors(query, exception)
        runTestBatchingExpectErrors(query, exception)
    }

    private void runTestBatchingUnbatchedExpectErrors(String query, Exception exception) {
        def errors = this.graphQLBatchedButUnbatched.execute(query).getErrors()
        assert errors.size() == 1
        assert exception.class == ((ExceptionWhileDataFetching) errors.get(0)).getException().class
        assert exception.getMessage() == ((ExceptionWhileDataFetching) errors.get(0)).getException().getMessage()
    }

    private void runTestBatchingExpectErrors(String query, Exception exception, boolean checkString = true) {
        def errors = this.graphQLBatchedValue.execute(query).getErrors()
        assert errors.size() == 1
        assert exception.class == ((ExceptionWhileDataFetching) errors.get(0)).getException().class
        if (checkString)
            assert exception.getMessage() == ((ExceptionWhileDataFetching) errors.get(0)).getException().getMessage()
    }

    private void runTestAsyncExpectErrors(String query, Exception exception) {
        def errors = this.graphQLAsync.execute(query).getErrors()
        assert errors.size() == 1
        assert exception.class == ((ExceptionWhileDataFetching) errors.get(0)).getException().class
        assert exception.getMessage() == ((ExceptionWhileDataFetching) errors.get(0)).getException().getMessage()
    }

    // Split into sub-methods so the stack trace is more useful
    private void runTest(String query, Map<String, Object> expected) {
        runTestAsync(query, expected)
        runTestBatchingUnbatched(query, expected)
        runTestBatching(query, expected)
        // check instrumentation recorded invocations
        assert !testingInstrumentation.dfInvocations.isEmpty()
    }

    private void runTestBatchingUnbatched(String query, Map<String, Object> expected) {
        assert this.graphQLBatchedButUnbatched.execute(query).getData() == expected
    }

    private void runTestBatching(String query, Map<String, Object> expected) {
        assert this.graphQLBatchedValue.execute(query).getData() == expected
    }

    private void runTestAsync(String query, Map<String, Object> expected) {
        assert this.graphQLAsync.execute(query).getData() == expected
    }

    // This method is agnostic to whether errors are returned or thrown, provided they contain the desired text
    private void runTestExpectError(String query, String errorSubstring) {

        try {
            ExecutionResult result = this.graphQLAsync.execute(query)
            assert !result.getErrors().isEmpty(), "Simple should have errored but was: " + result.getData()
        } catch (Exception e) {
            assert e.getMessage().contains(errorSubstring), "Simple error must contain '" + errorSubstring + "'"
        }

        try {
            ExecutionResult result = this.graphQLBatchedButUnbatched.execute(query)
            assert !result.getErrors().isEmpty(), "Batched should have errored, but was " + result.getData()
        } catch (Exception e) {
            assert e.getMessage().contains(errorSubstring), "Batched but unbatched error must contain '" + errorSubstring + "'"
        }
    }

    static Map<String, Object> mapOf(String firstKey, Object firstVal, Object... more) {
        Map<String, Object> retVal = new HashMap<>()
        retVal.put(firstKey, firstVal)
        for (int i = 0; i < more.length; i += 2) {
            retVal.put((String) more[i], more[i + 1])
        }
        return retVal
    }


    def "Basic case works"() {
        given:
        String query = "{ string(value: \"Basic\"){value, nonNullValue, veryNonNullValue} }"

        def expected = [string: [veryNonNullValue: "Basic", nonNullValue: "Basic", value: "Basic"]]
        println expected

        expect:
        runTest(query, expected)
    }

    def "Empty input"() {
        given:
        String query = "{ string(value: \"\"){value} }"

        def expected = [string: ["value": ""]]

        expect:
        runTest(query, expected)
    }

    def "Handles implicit null input"() {
        given:
        String query = "{ string{value} }"

        def expected = [string: null]

        expect:
        runTest(query, expected)
    }

    def "Handles explicit null input"() {
        given:
        String query = "{ string(value: \"null\"){value} }"

        def expected = [string: [value: null]]

        expect:
        runTest(query, expected)
    }

    def "Shatter works"() {
        given:
        String query = "{ string(value: \"Shatter\") {shatter{value}} }"

        def expected = ["string": ["shatter": [
                ["value": "S"],
                ["value": "h"],
                ["value": "a"],
                ["value": "t"],
                ["value": "t"],
                ["value": "e"],
                ["value": "r"]
        ]]]


        expect:
        runTest(query, expected)

    }

    def "Shatter then append"() {
        given:
        String query =
                "{ string(value: \"Sh\") { shatter { append(text: \"1\") { value } } } }"

        def expected = [string: [shatter: [[append: [value: "S1"]], [append: [value: "h1"]]]]]

        expect:
        runTest(query, expected)

    }


    def "Legal null entries in lists"() {
        given:
        String query =
                "{ " +
                        "string(value: \"Sh\") {" +
                        "shatter { " +
                        "append(text: \"1\") {" +
                        "split(regex: \"h\") {" +
                        "value " +
                        "} " +
                        "} " +
                        "} " +
                        "} " +
                        "}"

        def expected = [string: [shatter: [[append: [split: [[value: "S1"]]]], [append: [split: [null, [value: "1"]]]]]]]

        expect:
        runTest(query, expected)

    }

    def "Legal null values for entire lists"() {

        given:
        String query =
                "{ " +
                        "string(value: \"Sh\") {" +
                        "shatter { " +
                        "append(text: \"1\") {" +
                        "split {" +
                        "value " +
                        "} " +
                        "} " +
                        "} " +
                        "} " +
                        "}"

        def expected = [string: [shatter: [[append: [split: null]], [append: [split: null]]]]]

        expect:
        runTest(query, expected)

    }


    def "Legal null values for primitives"() {

        given:
        String query =
                "{ " +
                        "string(value: \"Shxnull\") {" +
                        "split(regex: \"x\") {" +
                        "value " +
                        "} " +
                        "} " +
                        "}"

        def expected = [string: [split: [[value: "Sh"], [value: null]]]]

        expect:
        runTest(query, expected)

    }

    def "Legal null value for enum"() {

        given:
        String query =
                "{ nullEnum }"

        def expected = [nullEnum: null]

        expect:
        runTest(query, expected)

    }

    def "Illegal null value for an object in a list"() {
        given:
        String query =
                "{ " +
                        "string(value: \"Sh\") {" +
                        "shatter { " +
                        "append(text: \"1\") {" +
                        "splitNonNull(regex: \"h\") {" +
                        "value " +
                        "} " +
                        "} " +
                        "} " +
                        "} " +
                        "}"

        expect:
        runTestExpectError(query, "non-null")

    }


    def "Nested lists"() {
        given:
        String query =
                "{ " +
                        "string(value: \"List of words\") {" +
                        "wordsAndLetters { " +
                        " value " +
                        "} " +
                        "} " +
                        "}"

        def expected = [string: [wordsAndLetters: [[[value: "L"], [value: "i"], [value: "s"], [value: "t"]],
                                                   [[value: "o"], [value: "f"]],
                                                   [[value: "w"], [value: "o"], [value: "r"], [value: "d"], [value: "s"]]]]]

        expect:
        runTest(query, expected)

    }


    def "Batching works"() {
        given:
        String query = """
                { string(value: "Batch") {
                         append(text: "x") {
                            value
                        }
                        shatter {
                            append(text: "1") {
                                split(regex: "h") {
                                    value
                                }
                            }
                        }
                    }
                }"""

        def expected = [string:
                                [append : [value: "Batchx"],
                                 shatter:
                                         [[append: [split: [[value: "B1"]]]],
                                          [append: [split: [[value: "a1"]]]],
                                          [append: [split: [[value: "t1"]]]],
                                          [append: [split: [[value: "c1"]]]],
                                          [append: [split: [null, [value: "1"]]]]]
                                ]
        ]
        expect:
        runTest(query, expected)

        this.countMap.get(FunWithStringsSchemaFactory.CallType.VALUE).get() == 2
        this.countMap.get(FunWithStringsSchemaFactory.CallType.SHATTER).get() == 1
        this.countMap.get(FunWithStringsSchemaFactory.CallType.APPEND).get() == 2
        this.countMap.get(FunWithStringsSchemaFactory.CallType.SPLIT).get() == 1
    }


    def "Return value ordering"() {
        given:
        String query = """
                { string(value: "TS") {
                        append(text:"1") {
                            v1:value
                            v2:nonNullValue
                            v3:veryNonNullValue
                            v4:value
                            v5:nonNullValue
                            v6:veryNonNullValue
                            v7:value
                            v8:nonNullValue
                            v9:veryNonNullValue
                        }
                    }
                }"""

        expect:
        Arrays.asList(this.graphQLAsync, this.graphQLBatchedButUnbatched, this.graphQLBatchedValue).each { GraphQL graphQL ->
            Map<String, Object> response = graphQL.execute(query).getData() as Map<String, Object>
            Map<String, Object> values = (response.get("string") as Map<String, Object>).get("append") as Map<String, Object>
            assert ["v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9"] == values.keySet().toList()
        }
    }

    def "Handle exception inside DataFetcher"() {
        given:
        String query = "{ string(value: \"\"){ throwException} }"
        Map<String, Object> expected = ["string": ["throwException": null]]
        expect:
        runTest(query, expected)
        runTestExpectErrors(query, new RuntimeException("TestException"))
    }

    def "Invalid batch size return does not crash whole query but generates error"() {
        given:
        String query = "{ string(value: \"\"){ returnBadList } }"
        Map<String, Object> expected = ["string": ["returnBadList": null]]
        expect:
        runTestBatching(query, expected)
        runTestBatchingExpectErrors(query, new BatchAssertionFailed(), false)
    }

    def "#673 optional support"() {
        given:
        String query = "{ string(value: \"673-optional-support\"){emptyOptional, optional} }"

        def expected = [string: [emptyOptional: null, optional: "673-optional-support"]]
        println expected

        expect:
        runTest(query, expected)
    }


    def "Any Iterable is accepted as GraphQL list value"() {
        given:
        String query = "{ string(value: \"test\"){ anyIterable } }"
        Map<String, Object> expected = ["string": ["anyIterable": ["test", "end"]]]
        expect:
        runTest(query, expected)
    }

    def "#672-683 handles completable futures ok"() {

        given:
        String query = "{ string(value: \"test\"){ completableFuture } }"
        Map<String, Object> expected = ["string": ["completableFuture": "completableFuture"]]
        expect:
        runTest(query, expected)
    }

    def "#672-683 handles completable futures ok in interfaces"() {

        given:
        String query = "{ interface { value } }"
        Map<String, Object> expected = ["interface": ["value": "interfacesHandled"]]
        expect:
        runTest(query, expected)
    }

    def "#684 handles exceptions in DFs"() {

        given:
        def UserType = newObject()
                .name("User")
                .field(newFieldDefinition()
                .name("id")
                .type(nonNull(Scalars.GraphQLInt))
                .dataFetcher(
                { e -> throw new RuntimeException("Hello") }
        )).build()

        DataFetcher userDataFetcher = { environment ->
            if (environment.getArgument("nullArg") == true) {
                return null
            }
            Map<String, Object> user = new HashMap<>()
            user.put("id", 1)
            return user
        }
        GraphQLObjectType queryObject = newObject().name("Query")
                .field(newFieldDefinition()
                .name("user")
                .type(nonNull(UserType))
                .argument(newArgument().name("nullArg").type(Scalars.GraphQLBoolean))
                .dataFetcher(userDataFetcher)
        ).build()

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryObject)
                .build()

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new BatchedExecutionStrategy())
                .build()

        //ExecutionResult result = graphQL.execute("query { user(nullArg:false) { id } }")
        ExecutionResult result = graphQL.execute("query { user { id } }")

        expect:
        result.getErrors().size() == 1
        result.getErrors()[0] instanceof ExceptionWhileDataFetching
    }

}
