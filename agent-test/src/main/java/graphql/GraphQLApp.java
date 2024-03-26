package graphql;

import graphql.agent.result.ExecutionTrackingResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Used for testing loading the agent on startup.
 * See StartAgentOnStartupTest
 */
public class GraphQLApp {

    public static void main(String[] args) {
        String schema = "type Query { hello: String }";
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(schema);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", environment -> "world"))
                .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{ hello alias: hello alias2: hello }").build();
        GraphQLContext graphQLContext = executionInput.getGraphQLContext();
        ExecutionResult executionResult = graphQL.execute(executionInput);
        System.out.println(executionResult.getData().toString());
        ExecutionTrackingResult executionTrackingResult = graphQLContext.get(ExecutionTrackingResult.EXECUTION_TRACKING_KEY);
        if (executionTrackingResult == null) {
            System.out.println("No tracking data found");
            System.exit(1);
        }
        if (executionTrackingResult.timePerPath.size() != 3) {
            System.out.println("Expected 3 paths, got " + executionTrackingResult.timePerPath.size());
            System.exit(1);
        }
        System.out.println("Successfully tracked execution");
        System.exit(0);
    }
}
