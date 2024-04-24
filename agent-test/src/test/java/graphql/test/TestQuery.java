package graphql.test;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.agent.result.ExecutionTrackingResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.assertj.core.api.Assertions;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TestQuery {


    static ExecutionTrackingResult executeQuery() {
        String sdl = "type Query{issues: [Issue]} type Issue {id: ID, title: String}";
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
        DataFetcher<List> issuesDF = (env) -> {
            return List.of(
                Map.of("id", "1", "title", "issue-1"),
                Map.of("id", "2", "title", "issue-2"));
        };

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder.dataFetcher("issues", issuesDF))
            .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{issues{id title}}").build();
        ExecutionResult result = graphQL.execute(executionInput);
        Assertions.assertThat(result.getErrors()).isEmpty();
        ExecutionTrackingResult trackingResult = executionInput.getGraphQLContext().get(ExecutionTrackingResult.EXECUTION_TRACKING_KEY);
        return trackingResult;
    }

    static ExecutionTrackingResult executeBatchedQuery() {
        String sdl = "type Query{issues: [Issue]} " +
            "type Issue {id: ID, author: User}" +
            "type User {id: ID, name: String}";

        DataFetcher<List> issuesDF = (env) -> List.of(
            Map.of("id", "1", "title", "issue-1", "authorId", "user-1"),
            Map.of("id", "2", "title", "issue-2", "authorId", "user-2"));

        BatchLoader<String, Map> userBatchLoader = keys -> {
            // System.out.println("batch users with keys: " + keys);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return List.of(
                    Map.of("id", "user-1", "name", "Foo-1"),
                    Map.of("id", "user-2", "name", "Foo-2")
                );
            });
        };
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("userLoader", DataLoaderFactory.newDataLoader(userBatchLoader));

        DataFetcher<CompletableFuture<Map>> authorDF = (env) -> {
            DataLoader<String, Map> userLoader = env.getDataLoader("userLoader");
            // System.out.println("author id: " + (String) ((Map) env.getSource()).get("authorId"));
            return userLoader.load((String) ((Map) env.getSource()).get("authorId"));
        };
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder.dataFetcher("issues", issuesDF))
            .type("Issue", builder -> builder.dataFetcher("author", authorDF))
            .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        String query = "{issues" +
            "{id author {id name}}" +
            "}";
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .dataLoaderRegistry(dataLoaderRegistry)
            .query(query).build();
        ExecutionResult result = graphQL.execute(executionInput);
        Assertions.assertThat(result.getErrors()).isEmpty();
        ExecutionTrackingResult trackingResult = executionInput.getGraphQLContext().get(ExecutionTrackingResult.EXECUTION_TRACKING_KEY);
        return trackingResult;
    }


}
