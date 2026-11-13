package graphql.normalized;

import graphql.Internal;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;

@Internal
public class VariableValueWithDefinition {
    private final Object value;
    private final VariableDefinition definition;
    private final VariableReference variableReference;

    public VariableValueWithDefinition(Object value, VariableDefinition definition, VariableReference variableReference) {
        this.value = value;
        this.definition = definition;
        this.variableReference = variableReference;
    }

    public Object getValue() {
        return value;
    }

    public VariableDefinition getDefinition() {
        return definition;
    }

    public VariableReference getVariableReference() {
        return variableReference;
    }
}
