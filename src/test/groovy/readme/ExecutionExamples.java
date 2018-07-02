package readme;

import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.StarWarsSchema;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutorServiceExecutionStrategy;
import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchema;
import graphql.schema.visibility.BlockedFields;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static graphql.StarWarsSchema.queryType;

@SuppressWarnings({"unused", "UnnecessaryLocalVariable", "Convert2Lambda", "unused", "ClassCanBeStatic", "TypeParameterUnusedInFormals"})
public class ExecutionExamples {

    public static void main(String[] args) throws Exception {
        new ExecutionExamples().simpleQueryExecution();
    }

    private void simpleQueryExecution() throws Exception {
        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("query { hero { name } }")
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        Object data = executionResult.getData();
        List<GraphQLError> errors = executionResult.getErrors();
    }

    @SuppressWarnings({"Convert2MethodRef","unused","FutureReturnValueIgnored"})
    private void simpleAsyncQueryExecution() throws Exception {
        GraphQL graphQL = buildSchema();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("query { hero { name } }")
                .build();

        CompletableFuture<ExecutionResult> promise = graphQL.executeAsync(executionInput);

        promise.thenAccept(executionResult -> {
            // here you might send back the results as JSON over HTTP
            encodeResultToJsonAndSendResponse(executionResult);
        });

        promise.join();
    }

    private GraphQL graphQL = buildSchema();
    private ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("query { hero { name } }")
            .build();

    private void equivalentSerialAndAsyncQueryExecution() throws Exception {

        ExecutionResult executionResult = graphQL.execute(executionInput);

        // the above is equivalent to the following code (in long hand)

        CompletableFuture<ExecutionResult> promise = graphQL.executeAsync(executionInput);
        ExecutionResult executionResult2 = promise.join();

    }

    @SuppressWarnings("Convert2Lambda")
    private void simpleDataFetcher() {
        DataFetcher userDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                return fetchUserFromDatabase(environment.getArgument("userId"));
            }
        };
    }

    @SuppressWarnings({"Convert2Lambda", "CodeBlock2Expr"})
    private void asyncDataFetcher() {

        DataFetcher userDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                CompletableFuture<User> userPromise = CompletableFuture.supplyAsync(
                        () -> {
                            return fetchUserFromDatabase(environment.getArgument("userId"));
                        });
                return userPromise;
            }
        };
    }

    private void succinctAsyncDataFetcher() {

        DataFetcher userDataFetcher = environment -> CompletableFuture.supplyAsync(
                () -> fetchUserFromDatabase(environment.getArgument("userId")));
    }

    private void wireInExecutionStrategies() {

        GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsyncExecutionStrategy())
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy())
                .build();

    }

    private void exampleExecutorServiceExecutionStrategy() {
        ExecutorService executorService = new ThreadPoolExecutor(
                2, /* core pool size 2 thread */
                2, /* max pool size 2 thread */
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(executorService))
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy())
                .build();
    }

    private void exceptionHandler() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            public void accept(DataFetcherExceptionHandlerParameters handlerParameters) {
                //
                // do your custom handling here.  The parameters have all you need
            }
        };
        ExecutionStrategy executionStrategy = new AsyncExecutionStrategy(handler);
    }

    private void blockedFields() {
        GraphqlFieldVisibility blockedFields = BlockedFields.newBlock()
                .addPattern("Character.id")
                .addPattern("Droid.appearsIn")
                .addPattern(".*\\.hero") // it uses regular expressions
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(blockedFields)
                .build();
    }

    private void noIntrospection() {

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY)
                .build();
    }

    class YourUserAccessService {

        public boolean isAdminUser() {
            return false;
        }
    }

    class CustomFieldVisibility implements GraphqlFieldVisibility {

        final YourUserAccessService userAccessService;

        CustomFieldVisibility(YourUserAccessService userAccessService) {
            this.userAccessService = userAccessService;
        }

        @Override
        public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
            if ("AdminType".equals(fieldsContainer.getName())) {
                if (!userAccessService.isAdminUser()) {
                    return Collections.emptyList();
                }
            }
            return fieldsContainer.getFieldDefinitions();
        }

        @Override
        public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
            if ("AdminType".equals(fieldsContainer.getName())) {
                if (!userAccessService.isAdminUser()) {
                    return null;
                }
            }
            return fieldsContainer.getFieldDefinition(fieldName);
        }
    }

    private void sendAsJson(Map<String, Object> toSpecificationResult) {
    }

    public void toSpec() throws Exception {

        ExecutionResult executionResult = graphQL.execute(executionInput);

        Map<String, Object> toSpecificationResult = executionResult.toSpecification();

        sendAsJson(toSpecificationResult);
    }

    class CustomRuntimeException extends RuntimeException implements GraphQLError {
        @Override
        public Map<String, Object> getExtensions() {
            Map<String, Object> customAttributes = new LinkedHashMap<>();
            customAttributes.put("foo", "bar");
            customAttributes.put("fizz", "whizz");
            return customAttributes;
        }

        @Override
        public List<SourceLocation> getLocations() {
            return null;
        }

        @Override
        public ErrorType getErrorType() {
            return ErrorType.DataFetchingException;
        }
    }

    private class User {

    }


    private <U> U fetchUserFromDatabase(Object userId) {
        return null;
    }


    private void encodeResultToJsonAndSendResponse(ExecutionResult executionResult) {

    }

    private GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();


    private GraphQL buildSchema() {
        return GraphQL.newGraphQL(schema)
                .build();
    }

}
