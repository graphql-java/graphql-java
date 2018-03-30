package graphql.cats.runner;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.cats.model.Execute;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.InterfaceWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.ScalarWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnionWiringEnvironment;
import graphql.schema.idl.WiringFactory;

class ExecuteTestRunner {

    @SuppressWarnings("ConstantConditions")
    static TestResult runTest(TestContext ctx) {
        Execute execute = ctx.getTest().getAction().getExecute().get();
        String query = ctx.getTest().getGiven().getQuery();
        ExecutionInput executionInput = buildInput(query, execute);

        GraphQL graphQL = buildEngine(ctx.getSchema(), ctx.getData());

        ExecutionResult executionResult = graphQL.execute(executionInput);

        return assertResult(ctx, query, executionResult);
    }

    private static TestResult assertResult(TestContext ctx, String query, ExecutionResult executionResult) {
        return TestResult.failed(ctx.getTestName(),query, "not written yet");
    }


    private static ExecutionInput buildInput(String query, Execute execute) {
        ExecutionInput.Builder builder = ExecutionInput.newExecutionInput().query(query);
        builder.variables(execute.getVariables());
        execute.getOperationName().ifPresent(builder::operationName);
        execute.getTestValue().ifPresent(builder::root);
        return builder.build();
    }


    private static GraphQL buildEngine(String schema, Object backingData) {
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(schema);

        WiringFactory wiringFactory = new WiringFactory() {
            @Override
            public boolean providesScalar(ScalarWiringEnvironment environment) {
                return false;
            }

            @Override
            public GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
                return null;
            }

            @Override
            public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
                return false;
            }

            @Override
            public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
                return null;
            }

            @Override
            public boolean providesTypeResolver(UnionWiringEnvironment environment) {
                return false;
            }

            @Override
            public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
                return null;
            }

            @Override
            public boolean providesDataFetcherFactory(FieldWiringEnvironment environment) {
                return false;
            }

            @Override
            public <T> DataFetcherFactory<T> getDataFetcherFactory(FieldWiringEnvironment environment) {
                return null;
            }

            @Override
            public boolean providesDataFetcher(FieldWiringEnvironment environment) {
                return false;
            }

            @Override
            public DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
                return null;
            }
        };

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().wiringFactory(wiringFactory).build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);
        return GraphQL.newGraphQL(graphQLSchema).build();
    }

}
