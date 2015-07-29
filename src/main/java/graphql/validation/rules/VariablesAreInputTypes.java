package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

public class VariablesAreInputTypes extends AbstractRule {

    private SchemaUtil schemaUtil = new SchemaUtil();

    public VariablesAreInputTypes(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = getValidationUtil().getUnmodifiedType(variableDefinition.getType());

        GraphQLType type = getValidationContext().getSchema().getType(unmodifiedAstType.getName());
        if (type == null) return;
        if (!schemaUtil.isInputType(type)) {
            String message = "Wrong type for a variable";
            addError(new ValidationError(ValidationErrorType.NonInputTypeOnVariable, variableDefinition.getSourceLocation(), message));
        }
    }
}
