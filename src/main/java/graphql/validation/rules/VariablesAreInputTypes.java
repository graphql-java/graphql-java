package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLType;
import graphql.validation.*;

import static graphql.schema.GraphQLTypeUtil.isInput;

public class VariablesAreInputTypes extends AbstractRule {

    public VariablesAreInputTypes(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = ValidationUtil.getUnmodifiedType(variableDefinition.getType());

        GraphQLType type = getValidationContext().getSchema().getType(unmodifiedAstType.getName());
        if (type == null) return;
        if (!isInput(type)) {
            String message = "Wrong type for a variable";
            addError(ValidationErrorType.NonInputTypeOnVariable, variableDefinition.getSourceLocation(), message);
        }
    }
}
