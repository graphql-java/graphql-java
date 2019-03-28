package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.schema.GraphQLTypeUtil.isInput;
import static graphql.validation.ValidationErrorType.NonInputTypeOnVariable;

public class VariablesAreInputTypes extends AbstractRule {

    public VariablesAreInputTypes(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = getValidationUtil().getUnmodifiedType(variableDefinition.getType());

        GraphQLType type = getValidationContext().getSchema().getType(unmodifiedAstType.getName());
        if (type == null) return;
        if (!isInput(type)) {
            String message = i18n(NonInputTypeOnVariable, "VariablesAreInputTypes.wrongType");
            addError(NonInputTypeOnVariable, variableDefinition.getSourceLocation(), message);
        }
    }
}
