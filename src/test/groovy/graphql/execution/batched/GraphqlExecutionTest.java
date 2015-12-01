package graphql.execution.batched;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.SimpleExecutionStrategy;
import graphql.schema.GraphQLSchema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class GraphqlExecutionTest extends Assert {

    private GraphQLSchema schema;
    private GraphQL graphQLSimple;
    private GraphQL graphQLBatchedButUnbatched;

    private GraphQL graphQLBatchedValue;

    private Map<String, Object> nullValueMap;

    private Map<FunWithStringsSchemaFactory.CallType, AtomicInteger> countMap;

    @Before
    public void createSchema() {

        nullValueMap = new HashMap<>();
        nullValueMap.put("value", null);

        this.schema = new FunWithStringsSchemaFactory().createSchema();
        this.graphQLSimple = new GraphQL(this.schema, new SimpleExecutionStrategy());
        this.graphQLBatchedButUnbatched = new GraphQL(this.schema, new BatchedExecutionStrategy());

        this.countMap = new HashMap<>();
        FunWithStringsSchemaFactory batchedValueFactory = FunWithStringsSchemaFactory.createBatched(countMap);

        this.graphQLBatchedValue = new GraphQL(batchedValueFactory.createSchema(), new BatchedExecutionStrategy());
    }

    // Split into sub-methods so the stack trace is more useful
    private void runTest(String query, Map<String, Object> expected) {
        runTestSimple(query, expected);
        runTestBatchingUnbatched(query, expected);
        runTestBatching(query, expected);
    }

    private void runTestExpectError(String query, String errorSubstring) {

        try {
            ExecutionResult result = this.graphQLSimple.execute(query);
            assertTrue("Simple should have errored but was: " + result.getData(), !result.getErrors().isEmpty());
        } catch (Exception e) {
            assertTrue("Simple error must contain '" + errorSubstring + "'", e.getMessage().contains(errorSubstring));
        }

        try {
            ExecutionResult result = this.graphQLBatchedButUnbatched.execute(query);
            assertTrue("Batched should have errored, but was " + result.getData(), !result.getErrors().isEmpty());
        } catch (Exception e) {
            assertTrue("Batched but unbatched error must contain '" + errorSubstring + "'", e.getMessage().contains(errorSubstring));
        }
    }

    private void runTestBatchingUnbatched(String query, Map<String, Object> expected) {
        assertEquals("BatchinGraphqlExecutionStrategy with normal data fetchers failed",
                expected, this.graphQLBatchedButUnbatched.execute(query).getData());
    }

    private void runTestBatching(String query, Map<String, Object> expected) {
        assertEquals("BatchinGraphqlExecutionStrategy with normal data fetchers failed",
                expected, this.graphQLBatchedValue.execute(query).getData());
    }


    private void runTestSimple(String query, Map<String, Object> expected) {
        assertEquals("SimpleExecutionStrategy failed",
                expected, this.graphQLSimple.execute(query).getData());
    }

    private Map<String, Object> mapOf(String firstKey, Object firstVal, Object... more) {
        Map<String, Object> retVal = new HashMap<>();
        retVal.put(firstKey, firstVal);
        for (int i = 0; i < more.length; i += 2) {
            retVal.put((String) more[i], more[i+1]);
        }
        return retVal;
    }

    @Test
    public void testBasic() {

        String query = "{ string(value: \"Basic\"){value, nonNullValue, veryNonNullValue} }";

        Map<String, Object> expected = mapOf("string", mapOf("value", "Basic", "nonNullValue", "Basic", "veryNonNullValue", "Basic"));

        runTest(query, expected);
    }

    @Test
    public void testEmpty() {

        String query = "{ string(value: \"\"){value} }";

        Map<String, Object> expected = mapOf("string", mapOf("value", ""));

        runTest(query, expected);
    }

    @Test
    public void testRootNull() {

        String query = "{ string{value} }";

        Map<String, Object> expected = new HashMap<>();
        expected.put("string", null);


        runTest(query, expected);
    }

    @Test
    public void testNull() {

        String query = "{ string(value: \"null\"){value} }";

        Map<String, Object> expected = mapOf("string", nullValueMap);

        runTest(query, expected);
    }

    @Test
    public void testShatter() {

        String query = "{ string(value: \"Shatter\"){shatter{value}} }";

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                        mapOf("value", "S"),
                        mapOf("value", "h"),
                        mapOf("value", "a"),
                        mapOf("value", "t"),
                        mapOf("value", "t"),
                        mapOf("value", "e"),
                        mapOf("value", "r")
                )));

        runTest(query, expected);

    }

    @Test
    public void testShatterAppend() {

        String query =
                "{ "
                        + "string(value: \"Sh\") {"
                            + "shatter { "
                                    + "append(text: \"1\") {"
                                          + "value "
                                    + "} "
                            + "} "
                        + "} "
                 + "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                        mapOf("append", mapOf("value", "S1")),
                        mapOf("append", mapOf("value", "h1"))
                )));

        runTest(query, expected);

    }

    @Test
    public void testNullsThatAreOk() {

        String query =
                "{ "
                        + "string(value: \"Sh\") {"
                        + "shatter { "
                        + "append(text: \"1\") {"
                        + "split(regex: \"h\") {"
                        + "value "
                        + "} "
                        + "} "
                        + "} "
                        + "} "
                        + "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                        mapOf("append", mapOf("split", Arrays.asList(
                                mapOf("value", "S1")))),
                        mapOf("append", mapOf("split", Arrays.asList(
                                null, mapOf("value", "1"))))
                )));

        runTest(query, expected);

    }

    @Test
    public void testNullListsThatAreOk() {

        String query =
                "{ "
                        + "string(value: \"Sh\") {"
                        + "shatter { "
                        + "append(text: \"1\") {"
                        + "split {"
                        + "value "
                        + "} "
                        + "} "
                        + "} "
                        + "} "
                        + "}";

        Map<String, Object> nullSplit = new HashMap<>();
        nullSplit.put("split", null);

        Map<String, Object> expected = mapOf(
                "string", mapOf("shatter", Arrays.asList(
                        mapOf("append", nullSplit),
                        mapOf("append", nullSplit)
                )));

        runTest(query, expected);

    }


    @Test
    public void testNullsThatAreOkInPrimitives() {

        String query =
                "{ "
                        + "string(value: \"Shxnull\") {"
                        + "split(regex: \"x\") {"
                        + "value "
                        + "} "
                        + "} "
                        + "}";

        Map<String, Object> expected = mapOf(
                "string", mapOf("split", Arrays.asList(
                        mapOf("value", "Sh"),
                        nullValueMap
                )));

        runTest(query, expected);

    }

    @Test
    public void testNullsThatAreNotOkInPrimitives() {

        String query =
                "{ "
                        + "string(value: \"Shxnull\") {"
                        + "split(regex: \"x\") {"
                        + "nonNullValue "
                        + "} "
                        + "} "
                        + "}";

        runTestExpectError(query, "non-null");
    }

    @Test
    public void testNullsThatAreNotOkInObjects() {

        String query =
                "{ "
                        + "string(value: \"\") {"
                        + "shatter { "
                        + "value "
                        + "} "
                        + "} "
                        + "}";

        runTestExpectError(query, "non-null");

    }

    @Test
    public void testNullsThatAreNotOkInPrimitivesDoubled() {

        String query =
                "{ "
                        + "string(value: \"Shxnull\") {"
                        + "split(regex: \"x\") {"
                        + "veryNonNullValue "
                        + "} "
                        + "} "
                        + "}";

        runTestExpectError(query, "non-null");
    }

    @Test
    public void testNullsThatAreNotOkInObjectsWithinLists() {

        String query =
                "{ "
                        + "string(value: \"Sh\") {"
                        + "shatter { "
                        + "append(text: \"1\") {"
                        + "splitNonNull(regex: \"h\") {"
                        + "value "
                        + "} "
                        + "} "
                        + "} "
                        + "} "
                        + "}";

        runTestExpectError(query, "non-null");

    }


    @Test
    public void testNestedLists() {

        String query =
                "{ "
                        + "string(value: \"List of words\") {"
                        + "wordsAndLetters { "
                        + " value "
                        + "} "
                        + "} "
                        + "}";

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

        runTest(query, expected);

    }

    @Test
    public void testBatching() {

        String query =
                "{ "
                        + "string(value: \"Batch\") {"
                        + "shatter { "
                        + "append(text: \"1\") {"
                        + "split(regex: \"h\") {"
                        + "value "
                        + "} "
                        + "} "
                        + "} "
                        + "} "
                        + "}";

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

        runTest(query, expected);

        assertEquals(1, this.countMap.get(FunWithStringsSchemaFactory.CallType.VALUE).get());
        assertEquals(1, this.countMap.get(FunWithStringsSchemaFactory.CallType.SHATTER).get());
        assertEquals(1, this.countMap.get(FunWithStringsSchemaFactory.CallType.APPEND).get());
        assertEquals(1, this.countMap.get(FunWithStringsSchemaFactory.CallType.SPLIT).get());
    }

}
