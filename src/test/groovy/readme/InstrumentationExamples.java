package readme;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.instrumentation.tracing.TracingInstrumentation;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.StarWarsSchema.queryType;

@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
public class InstrumentationExamples {

    private void specifyInstrumentation() {

        GraphQL.newGraphQL(schema)
                .instrumentation(new TracingInstrumentation())
                .build();

    }

    private GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();


    private GraphQL buildSchema() {
        return GraphQL.newGraphQL(schema)
                .build();
    }

    class CustomInstrumentationState implements InstrumentationState {
        private Map<String, Object> anyStateYouLike = new HashMap<>();

        void recordTiming(String key, long time) {
            anyStateYouLike.put(key, time);
        }
    }

    class CustomInstrumentation implements Instrumentation {
        @Override
        public InstrumentationState createState() {
            //
            // instrumentation state is passed during each invocation of an Instrumentation method
            // and allows you to put stateful data away and reference it during the query execution
            //
            return new CustomInstrumentationState();
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
            long startNanos = System.nanoTime();
            return (result, throwable) -> {

                CustomInstrumentationState state = parameters.getInstrumentationState();
                state.recordTiming(parameters.getQuery(), System.nanoTime() - startNanos);
            };
        }

        @Override
        public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
            //
            // You MUST return a non null object but it does not have to do anything and hence
            // you use this class to return a no-op object
            //
            return new NoOpInstrumentation.NoOpInstrumentationContext<>();
        }

        @Override
        public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
            return new NoOpInstrumentation.NoOpInstrumentationContext<>();
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
            return new NoOpInstrumentation.NoOpInstrumentationContext<>();
        }

        @Override
        public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
            return new NoOpInstrumentation.NoOpInstrumentationContext<>();
        }

        @Override
        public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
            return new NoOpInstrumentation.NoOpInstrumentationContext<>();
        }

        @Override
        public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
            //
            // this allows you to intercept the data fetcher used ot fetch a field and provide another one, perhaps
            // that enforces certain behaviours or has certain side effects on the data
            //
            return dataFetcher;
        }

        @Override
        public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
            //
            // this allows you to instrument the execution result some how.  For example the Tracing support uses this to put
            // the `extensions` map of data in place
            //
            return CompletableFuture.completedFuture(executionResult);
        }
    }

    private class FooInstrumentation extends CustomInstrumentation {

    }

    private class BarInstrumentation extends CustomInstrumentation {

    }

    private void chained() {
        List<Instrumentation> chainedList = new ArrayList<>();
        chainedList.add(new FooInstrumentation());
        chainedList.add(new BarInstrumentation());
        ChainedInstrumentation chainedInstrumentation = new ChainedInstrumentation(chainedList);

        GraphQL.newGraphQL(schema)
                .instrumentation(chainedInstrumentation)
                .build();

    }

}
