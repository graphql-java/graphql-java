package graphql;


import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Map;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.junit.Assert.assertEquals;

public class HelloWorld {

    private static GraphQL buildGraphQL() {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .staticValue("world"))
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        return GraphQL.newGraphQL(schema).build();
    }

    private static final String query ="{\n" +
            "hello\n" +
            "}";

    public static void main(String[] args) {
        GraphQL graphQL = buildGraphQL();
        ExecutionResult executionResult = graphQL.execute(query);
        if (executionResult.getErrors().size() != 0) {
            System.err.println(executionResult.getErrors().toString());
        }
        Map<String, Object> result = executionResult.getData();
        System.out.println(result);
        // Prints: {hello=world}
    }

    @Test
    public void helloWorldTest() {
        GraphQL graphQL = buildGraphQL();
        ExecutionResult executionResult = graphQL.execute(query);
        assertEquals(0, executionResult.getErrors().size());
        Map<String, Object> result = executionResult.getData();
        assertEquals(1, result.size());
        assertEquals("world", result.get("hello"));
    }
}
