package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.language.Document;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.Map;

/**
 * Nothing to see here
 */
public final class NoOpInstrumentation implements Instrumentation {

    public static NoOpInstrumentation INSTANCE = new NoOpInstrumentation();

    private NoOpInstrumentation() {
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        return new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onEnd(ExecutionResult result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<Document> beginParse(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        return new InstrumentationContext<Document>() {
            @Override
            public void onEnd(Document result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(Document document) {
        return new InstrumentationContext<List<ValidationError>>() {
            @Override
            public void onEnd(List<ValidationError> result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef) {
        return new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onEnd(ExecutionResult result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<Object> beginDataFetch(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment) {
        return new InstrumentationContext<Object>() {
            @Override
            public void onEnd(Object result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }
}
