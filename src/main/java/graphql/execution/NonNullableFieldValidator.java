package graphql.execution;


/**
 * This will check that a value is non null when the type definition says it must be and it will throw {@link NonNullableFieldWasNullException}
 * if this is not the case.
 *
 * See: http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
 */
public class NonNullableFieldValidator {
    private final ExecutionContext executionContext;
    private final TypeInfo typeInfo;

    public NonNullableFieldValidator(ExecutionContext executionContext, TypeInfo typeInfo) {
        this.executionContext = executionContext;
        this.typeInfo = typeInfo;
    }

    /**
     * Called to check that a value is non null if the type requires it to be non null
     *
     * @param result the result to check
     *
     * @return the result back
     *
     * @throws NonNullableFieldWasNullException if the value is null but the type requires it to be non null
     */
    <T> T validate(T result) throws NonNullableFieldWasNullException {
        if (result == null) {
            if (typeInfo.typeIsNonNull()) {
                // see http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
                NonNullableFieldWasNullException nonNullException = new NonNullableFieldWasNullException(typeInfo);
                executionContext.addError(nonNullException);
                throw nonNullException;
            }
        }
        return result;
    }

}
