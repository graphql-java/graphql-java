package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.Internal;

import java.util.function.Function;

@Internal
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return parseAndValidateFunction.apply(executionInput);
    }
}
