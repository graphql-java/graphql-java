package graphql.normalized;

import graphql.Internal;

/**
 * This predicate indicates whether a variable should be made for this field argument
 */
@Internal
public interface VariablePredicate {
    /**
     * Return true if a variable should be made for this field argument
     *
     * @param executableNormalizedField the field in question
     * @param argName                   the argument on the field
     * @param normalizedInputValue      the input value for that argument
     * @return true if a variable should be made
     */
    boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue);
}
