package graphql.validation.rules;


import graphql.execution.TypeFromAST;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLType;
import graphql.validation.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class VariableTypesMatchRule extends AbstractRule {

    VariablesTypesMatcher variablesTypesMatcher = new VariablesTypesMatcher();

    private Map<String, VariableDefinition> variableDefinitionMap;

    public VariableTypesMatchRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        variableDefinitionMap = new LinkedHashMap<>();
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableDefinitionMap.put(variableDefinition.getName(), variableDefinition);
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        VariableDefinition variableDefinition = variableDefinitionMap.get(variableReference.getName());
        if (variableDefinition == null) return;
        GraphQLType variableType = TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), variableDefinition.getType());
        if (variableType == null) return;
        GraphQLInputType inputType = getValidationContext().getInputType();
        if (!variablesTypesMatcher.doesVariableTypesMatch(variableType, variableDefinition.getDefaultValue(), inputType)) {
            String message = "Variable type doesn't match";
            addError(new ValidationError(ValidationErrorType.VariableTypeMismatch, variableReference.getSourceLocation(), message));
        }
    }


}
