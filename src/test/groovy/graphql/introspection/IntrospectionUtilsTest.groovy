package graphql.introspection

import spock.lang.Specification

import static graphql.introspection.IntrospectionUtils.isIntrospectionQuery;

class IntrospectionUtilsTest extends Specification {
    def TEST_INTROSPECTION_QUERY_SNIPPET =
            "    " + IntrospectionUtils.INTROSPECTION_SCHEMA_STRING + " {\n" +
                    "      queryType { name }\n" +
                    "    }";
    def HELLO_WORLD_QUERY_NAME = "hello";
    def HELLO_WORLD_QUERY = "query " + HELLO_WORLD_QUERY_NAME + " { helloWorld }";
    def INTROSPECTION_QUERY_BY_NAME = "query " + IntrospectionQuery.INTROSPECTION_QUERY_NAME + " { helloWorld }";
    def INTROSPECTION_QUERY_BY_SCHEMA_TYPE = "query " + HELLO_WORLD_QUERY_NAME + " { " + TEST_INTROSPECTION_QUERY_SNIPPET + "}";

    def "should detect introspection query based on operation name"() {
        expect:
        isIntrospectionQuery(IntrospectionQuery.INTROSPECTION_QUERY_NAME, null);
    }

    def "should detect introspection query based on query name"() {
        expect:
        isIntrospectionQuery(null, INTROSPECTION_QUERY_BY_NAME);
    }

    def "should detect introspection query based on query schema type"() {
        expect:
        isIntrospectionQuery(null, INTROSPECTION_QUERY_BY_SCHEMA_TYPE);
    }

    def "should NOT detect introspection query based on null inputs"() {
        expect:
        !isIntrospectionQuery(null, null);
    }

    def "should NOT detect introspection query based on empty inputs"() {
        expect:
        !isIntrospectionQuery("", "");
    }

    def "should NOT detect introspection query based on operation name"() {
        expect:
            !isIntrospectionQuery(HELLO_WORLD_QUERY_NAME, null);
    }

    def "should NOT detect introspection query based on query"() {
        expect:
        !isIntrospectionQuery(HELLO_WORLD_QUERY_NAME, HELLO_WORLD_QUERY);
    }
}
