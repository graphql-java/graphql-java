package graphql.execution.instrumentation.nextgen;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.language.Document;
import graphql.nextgen.ExecutionInput;
import graphql.schema.GraphQLSchema;

@Internal
public interface Instrumentation {

    default InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new InstrumentationState() {
        };
    }

    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return executionInput;
    }

    default GraphQLSchema instrumentSchema(GraphQLSchema graphQLSchema, InstrumentationExecutionParameters parameters) {
        return graphQLSchema;
    }

    default Document instrumentDocument(Document document, InstrumentationExecutionParameters parameters) {
        // TODO -
        return document;
    }

    default ExecutionResult instrumentExecutionResult(ExecutionResult result, InstrumentationExecutionParameters parameters) {
        return result;
    }

    default InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    default InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }
}
