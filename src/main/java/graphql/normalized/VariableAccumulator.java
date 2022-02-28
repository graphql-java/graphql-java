package graphql.normalized;

import graphql.Internal;
import graphql.language.VariableDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.normalized.ValueToVariableValueCompiler.normalizedInputValueToVariable;

/**
 * This accumulator class decides on whether to create a variable for a query argument and if so it tracks what variables were made.
 * The {@link ExecutableNormalizedOperationToAstCompiler} then uses all the variables when it compiles the final document.
 */
@Internal
public class VariableAccumulator {

    private final List<VariableValueWithDefinition> valueWithDefinitions;
    @Nullable
    private final VariablePredicate variablePredicate;

    public VariableAccumulator(@Nullable VariablePredicate variablePredicate) {
        this.variablePredicate = variablePredicate;
        valueWithDefinitions = new ArrayList<>();
    }

    public boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
        return variablePredicate != null && variablePredicate.shouldMakeVariable(executableNormalizedField, argName, normalizedInputValue);
    }

    public VariableValueWithDefinition accumulateVariable(NormalizedInputValue normalizedInputValue) {
        VariableValueWithDefinition variableWithDefinition = normalizedInputValueToVariable(normalizedInputValue, getAccumulatedSize());
        valueWithDefinitions.add(variableWithDefinition);
        return variableWithDefinition;
    }

    public int getAccumulatedSize() {
        return valueWithDefinitions.size();
    }

    /**
     * @return the variable definitions that would go on the operation declaration
     */
    public List<VariableDefinition> getVariableDefinitions() {
        return valueWithDefinitions.stream().map(VariableValueWithDefinition::getDefinition).collect(Collectors.toList());
    }

    /**
     * @return the map of variable names to variable values
     */
    public Map<String, Object> getVariablesMap() {
        return valueWithDefinitions.stream()
                .collect(Collectors.toMap(
                        variableWithDefinition -> variableWithDefinition.getDefinition().getName(),
                        VariableValueWithDefinition::getValue
                ));
    }
}
