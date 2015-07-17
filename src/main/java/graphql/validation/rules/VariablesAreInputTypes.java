package graphql.validation.rules;


import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

public class VariablesAreInputTypes extends AbstractRule {

    public VariablesAreInputTypes(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        TypeName unmodifiedAstType = getValidationUtil().getUnmodifiedType(variableDefinition.getType());

        GraphQLType type = SchemaUtil.findType(getValidationContext().getSchema(), unmodifiedAstType.getName());
        if (type == null) return;
        if (!SchemaUtil.isInputType(type)) {
            addError(new ValidationError(ValidationErrorType.NonInputTypeOnVariable));
        }
    }
}
