package graphql.analysis;

import graphql.ExecutionResult;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Definition;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

public class MaxBatchOperationsInstrumentation extends SimplePerformantInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(MaxBatchOperationsInstrumentation.class);

    private final int maxOperations;
    private final Function<RequestWidthInfo, Boolean> maxRequestedOperationsExceededFunction;

    /**
     * Creates a new instrumentation that tracks the request width.
     *
     * @param maxOperations max allowed operations, otherwise execution will be aborted
     */
    public MaxBatchOperationsInstrumentation(int maxOperations) {
        this(maxOperations, (requestWidthInfo) -> true);
    }

    /**
     * Creates a new instrumentation that tracks the request width.
     *
     * @param maxOperations                      max allowed width, otherwise execution will be aborted
     * @param maxRequestedOperationsExceededFunction the function to perform when the max width is exceeded
     */
    public MaxBatchOperationsInstrumentation(int maxOperations, Function<RequestWidthInfo, Boolean> maxRequestedOperationsExceededFunction) {
        this.maxOperations = maxOperations;
        this.maxRequestedOperationsExceededFunction = maxRequestedOperationsExceededFunction;
    }

    @Override
    public @Nullable InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        List<Definition> definitions = new ArrayList<>();
        if(parameters.getExecutionContext()!=null && parameters.getExecutionContext().getDocument()!=null && parameters.getExecutionContext().getDocument().getDefinitions()!=null) {
            definitions = parameters.getExecutionContext().getDocument().getDefinitions();
        }
        int supplied_width = 0;
        if (!definitions.isEmpty()) {
            for (Definition definition : definitions) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                SelectionSet selectionSet = operationDefinition.getSelectionSet();
                if (selectionSet != null && selectionSet.getSelections() != null) {
                    supplied_width += selectionSet.getSelections().size();
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Request width info: {}", supplied_width);
        }
        if (supplied_width > maxOperations) {
            RequestWidthInfo requestWidthInfo = RequestWidthInfo.newRequestWidthInfo()
                    .width(supplied_width)
                    .build();
            boolean throwAbortException = maxRequestedOperationsExceededFunction.apply(requestWidthInfo);
            if (throwAbortException) {
                throw mkAbortException(supplied_width, maxOperations);
            }
        }
        return noOp();
    }

    /**
     * Called to generate your own error message or custom exception class
     *
     * @param width    the width of the request
     * @param maxWidth the maximum width allowed
     *
     * @return an instance of AbortExecutionException
     */
    protected AbortExecutionException mkAbortException(int width, int maxWidth) {
        return new AbortExecutionException("maximum request width exceeded " + width + " > " + maxWidth);
    }
}
