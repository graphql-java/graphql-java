/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package graphql.execution.batched

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.SimpleExecutionStrategy
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class GraphqlExecutionTest extends Specification {

    private GraphQLSchema schema = new FunWithStringsSchemaFactory().createSchema();

    private GraphQL graphQLSimple = GraphQL.newGraphQL(schema)
            .queryExecutionStrategy(new SimpleExecutionStrategy())
            .build()

    private GraphQL graphQLBatchedButUnbatched = GraphQL.newGraphQL(this.schema)
            .queryExecutionStrategy(new BatchedExecutionStrategy())
            .build()

    private Map<FunWithStringsSchemaFactory.CallType, AtomicInteger> countMap = new HashMap<>();
    private GraphQL graphQLBatchedValue = GraphQL.newGraphQL(FunWithStringsSchemaFactory.createBatched(countMap).createSchema())
            .queryExecutionStrategy(new BatchedExecutionStrategy())
            .build()

    private Map<String, Object> nullValueMap = new HashMap<>();

    def setup() {
        nullValueMap.put("value", null);
    }

    // Split into sub-methods so the stack trace is more useful
    private void runTest(String query, Map<String, Object> expected) {
        runTestSimple(query, expected);
        runTestBatchingUnbatched(query, expected);
        runTestBatching(query, expected);
    }

    private void runTestBatchingUnbatched(String query, Map<String, Object> expected) {
        assert expected == this.graphQLBatchedButUnbatched.execute(query).getData();
    }

    private void runTestBatching(String query, Map<String, Object> expected) {
        assert expected == this.graphQLBatchedValue.execute(query).getData();
    }


    private void runTestSimple(String query, Map<String, Object> expected) {
        assert expected == this.graphQLSimple.execute(query).getData();
    }

    // This method is agnostic to whether errors are returned or thrown, provided they contain the desired text
    private void runTestExpectError(String query, String errorSubstring) {

        try {
            ExecutionResult result = this.graphQLSimple.execute(query);
            assert !result.getErrors().isEmpty(), "Simple should have errored but was: " + result.getData();
        } catch (Exception e) {
            assert e.getMessage().contains(errorSubstring), "Simple error must contain '" + errorSubstring + "'";
        }

        try {
            ExecutionResult result = this.graphQLBatchedButUnbatched.execute(query);
            assert !result.getErrors().isEmpty(), "Batched should have errored, but was " + result.getData();
        } catch (Exception e) {
            assert e.getMessage().contains(errorSubstring), "Batched but unbatched error must contain '" + errorSubstring + "'";
        }
    }

    private Map<String, Object> mapOf(String firstKey, Object firstVal, Object... more) {
        Map<String, Object> retVal = new HashMap<>();
        retVal.put(firstKey, firstVal);
        for (int i = 0; i < more.length; i += 2) {
            retVal.put((String) more[i], more[i + 1]);
        }
        return retVal;
    }


    def "Basic case works"() {
        given:
        String query = "{ string(value: \"Basic\"){value, nonNullValue, veryNonNullValue} }";

        Map<String, Object> expected = mapOf("string", mapOf("value", "Basic", "nonNullValue", "Basic", "veryNonNullValue", "Basic"));

        expect:
        runTest(query, expected);
    }

    def "Empty input"() {
        given:
        String query = "{ string(value: \"\"){value} }";

        Map<String, Object> expected = mapOf("string", mapOf("value", ""));

        expect:
        runTest(query, expected);
    }

    def "Handles implicit null input"() {
        given:
        String query = "{ string{value} }";

        Map<String, Object> expected = new HashMap<>();
        expected.put("string", null);

        expect:
        runTest(query, expected);
    }

    def "Handles explicit null input"() {
        given:
        String query = "{ string(value: \"null\"){value} }";

        Map<String, Object> expected = mapOf("string", nullValueMap);

        expect:
        runTest(query, expected);
    }

    def "Shatter works"() {
        given:
        String query = "{ string(value: \"Shatter\") {shatter{value}} }";

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
        runTest(query, expected);

    }

    def "Shatter then append"() {
        given:
        String query =
                "{ string(value: \"Sh\") { shatter { append(text: \"1\") { value } } } }";

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                mapOf("append", mapOf("value", "S1")),
                mapOf("append", mapOf("value", "h1"))
        )));

        expect:
        runTest(query, expected);

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
                        "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                mapOf("append", mapOf("split", Arrays.asList(
                        mapOf("value", "S1")))),
                mapOf("append", mapOf("split", Arrays.asList(
                        null, mapOf("value", "1"))))
        )));

        expect:
        runTest(query, expected);

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
                        "}";

        Map<String, Object> nullSplit = new HashMap<>();
        nullSplit.put("split", null);

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                mapOf("append", nullSplit),
                mapOf("append", nullSplit)
        )));

        expect:
        runTest(query, expected);

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
                        "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("split", Arrays.asList(
                mapOf("value", "Sh"),
                nullValueMap
        )));

        expect:
        runTest(query, expected);

    }

    def "Legal null value for enum"() {

        given:
        String query =
                "{ nullEnum }";

        Map<String, Object> expected = mapOf(
                "nullEnum", null);

        expect:
        runTest(query, expected);

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
                        "}";

        expect:
        runTestExpectError(query, "non-null");

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
                        "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("wordsAndLetters", Arrays.asList(
                Arrays.asList(
                        mapOf("value", "L"),
                        mapOf("value", "i"),
                        mapOf("value", "s"),
                        mapOf("value", "t")),
                Arrays.asList(
                        mapOf("value", "o"),
                        mapOf("value", "f")),
                Arrays.asList(
                        mapOf("value", "w"),
                        mapOf("value", "o"),
                        mapOf("value", "r"),
                        mapOf("value", "d"),
                        mapOf("value", "s"))

        )));

        expect:
        runTest(query, expected);

    }


    def "Batching works"() {
        given:
        String query = """
                { string(value: "Batch") {
                        shatter {
                            append(text: "1") {
                                split(regex: "h") {
                                    value
                                }
                            }
                        }
                    }
                }"""

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                mapOf("append", mapOf("split", Arrays.asList(
                        mapOf("value", "B1")))),
                mapOf("append", mapOf("split", Arrays.asList(
                        mapOf("value", "a1")))),
                mapOf("append", mapOf("split", Arrays.asList(
                        mapOf("value", "t1")))),
                mapOf("append", mapOf("split", Arrays.asList(
                        mapOf("value", "c1")))),
                mapOf("append", mapOf("split", Arrays.asList(
                        null, mapOf("value", "1"))))
        )));

        expect:
        runTest(query, expected);

        1 == this.countMap.get(FunWithStringsSchemaFactory.CallType.VALUE).get();
        1 == this.countMap.get(FunWithStringsSchemaFactory.CallType.SHATTER).get();
        1 == this.countMap.get(FunWithStringsSchemaFactory.CallType.APPEND).get();
        1 == this.countMap.get(FunWithStringsSchemaFactory.CallType.SPLIT).get();
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
        Arrays.asList(this.graphQLSimple, this.graphQLBatchedButUnbatched, this.graphQLBatchedValue).each { GraphQL graphQL ->
            Map<String, Object> response = graphQL.execute(query).getData() as Map<String, Object>;
            Map<String, Object> values = (response.get("string") as Map<String, Object>).get("append") as Map<String, Object>;
            assert Arrays.asList("v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9") == values.keySet().toList();
        }
    }


}
