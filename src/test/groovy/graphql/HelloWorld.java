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
                        .staticValue("world")
                        .build())
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        Map<String, Object> result = new GraphQL(schema).execute("{hello}").getData();
        System.out.println(result);
    }

    @Test
    public void helloWorldTest() {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .staticValue("world")
                        .build())
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        Map<String, Object> result = new GraphQL(schema).execute("{hello}").getData();
        assertEquals("world", result.get("hello"));
    }
}
