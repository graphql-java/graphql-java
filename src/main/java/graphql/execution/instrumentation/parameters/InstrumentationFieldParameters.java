package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLFieldDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@NullMarked
@PublicApi
public class InstrumentationFieldParameters {
    private final ExecutionContext executionContext;
    private final @Nullable GraphQLFieldDefinition fieldDefinition;
    private final Supplier<ExecutionStepInfo> executionStepInfo;

    public InstrumentationFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo) {
        this(executionContext, null, executionStepInfo);
    }

    /**
     * @param executionContext  the execution context
     * @param fieldDefinition   the field definition if it is already known, which allows {@link #getField()} to answer
     *                          without invoking the {@link ExecutionStepInfo} supplier, or null if it is not known
     * @param executionStepInfo a supplier of the {@link ExecutionStepInfo} for this field
     */
    public InstrumentationFieldParameters(ExecutionContext executionContext, @Nullable GraphQLFieldDefinition fieldDefinition, Supplier<ExecutionStepInfo> executionStepInfo) {
        this.executionContext = executionContext;
        this.fieldDefinition = fieldDefinition;
        this.executionStepInfo = executionStepInfo;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    /**
     * This returns the {@link GraphQLFieldDefinition} of the field being instrumented.
     * <p>
     * When the field definition was supplied at construction time (as the engine does), this is a cheap call that
     * does NOT invoke the {@link ExecutionStepInfo} supplier and hence does not build a
     * {@link graphql.schema.DataFetchingEnvironment} for the field.  Otherwise it falls back
     * to {@link #getExecutionStepInfo()} to find the field definition.
     *
     * @return the field definition of the field being instrumented
     */
    public GraphQLFieldDefinition getField() {
        if (fieldDefinition != null) {
            return fieldDefinition;
        }
        return assertNotNull(executionStepInfo.get().getFieldDefinition(), "fieldDefinition must not be null");
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

}
