package graphql.test;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.agent.result.ExecutionTrackingResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;

public class TestQuery {

    static DataFetcher<List> issuesDF = (env) -> {
        return List.of(
                Map.of("id", "1", "title", "issue-1"),
                Map.of("id", "2", "title", "issue-2"));
    };

    static ExecutionTrackingResult executeQuery() {
        String sdl = "type Query{issues: [Issue]} type Issue {id: ID, title: String}";
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("test");

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
}
