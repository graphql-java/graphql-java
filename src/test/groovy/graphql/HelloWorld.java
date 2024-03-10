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

    public static void main(String[] args) {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                )
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        GraphQL graphQL = GraphQL.newGraphQL(schema).build();


        Map<String, String> rootObject = Map.of("hello", "world");
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{hello}").root(rootObject).build();
        Map<String, Object> result = graphQL.execute(executionInput).getData();
        System.out.println(result);
        // Prints: {hello=world}
    }

    @Test
    public void helloWorldTest() {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                )
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        GraphQL graphQL = GraphQL.newGraphQL(schema).build();
        Map<String, String> rootObject = Map.of("hello", "world");
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{hello}").root(rootObject).build();

        Map<String, Object> result = graphQL.execute(executionInput).getData();
        assertEquals("world", result.get("hello"));
    }
}
