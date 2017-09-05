package graphql.execution.batched;

import graphql.Scalars;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AsyncTypeResolverSchema {

    public GraphQLSchema createSchema() {
        GraphQLObjectType foo = GraphQLObjectType.newObject()
            .name("Foo")
            .withInterface(new GraphQLTypeReference("Node"))
            .field(field -> field
                .name("id")
                .type(Scalars.GraphQLID))
            .build();

        GraphQLInterfaceType node = GraphQLInterfaceType.newInterface()
            .name("Node")
            .field(field -> field
                .name("id")
                .type(Scalars.GraphQLID))
            .typeResolver((env) -> {
                if (env.getObject() instanceof CompletionStage) {
                    throw new RuntimeException("TypeResolver received CompletionStage as environment object, this should not happen");
                }

                return foo;
            })
            .build();

        GraphQLObjectType query = GraphQLObjectType.newObject()
            .name("RootQuery")
            .field(field -> field
                .name("node")
                .dataFetcher(env ->
                    CompletableFuture.supplyAsync(() -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", "abc");
                        return map;
                    }))
                .type(node))
            .build();

        return GraphQLSchema.newSchema()
            .query(query)
            .build();
    }

}
