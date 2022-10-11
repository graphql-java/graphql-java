package graphql.execution.instrumentation.dataloader;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;

public class DeepDataFetchers {
    private static <T> CompletableFuture<T> supplyAsyncWithSleep(Supplier<T> supplier) {
        Supplier<T> sleepSome = sleepSome(supplier);
        return CompletableFuture.supplyAsync(sleepSome);
    }

    private static <T> Supplier<T> sleepSome(Supplier<T> supplier) {
        return () -> {
            try {
                Thread.sleep(10L);
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public GraphQLSchema schema() {
        GraphQLFieldDefinition selfField = GraphQLFieldDefinition.newFieldDefinition()
                .name("self")
                .type(GraphQLTypeReference.typeRef("Query"))
                .build();

        GraphQLObjectType query = GraphQLObjectType.newObject()
                .name("Query")
                .field(selfField)
                .build();

        FieldCoordinates selfCoordinates = FieldCoordinates.coordinates("Query", "self");
        DataFetcher<CompletableFuture<HashMap<String, Object>>> slowFetcher = environment ->
                supplyAsyncWithSleep(HashMap::new);

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(selfCoordinates, slowFetcher)
                .build();

        return GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(query)
                .build();
    }

    public String buildQuery(Integer depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("query {");
        for (Integer i = 0; i < depth; i++) {
            sb.append("self {");
        }
        sb.append("__typename");
        for (Integer i = 0; i < depth; i++) {
            sb.append("}");
        }
        sb.append("}");

        return sb.toString();
    }

    public HashMap<String, Object> buildResponse(Integer depth) {
        HashMap<String, Object> level = new HashMap<>();
        if (depth == 0) {
            level.put("__typename", "Query");
        } else {
            level.put("self", buildResponse(depth - 1));
        }
        return level;
    }
}
