package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.*;

public class FieldsOnCorrectType extends AbstractRule {


    public FieldsOnCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkField(Field field) {
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (parentType == null) return;
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) {
            String message = String.format("Field %s is undefined", field.getName());
            addError(new ValidationError(ValidationErrorType.FieldUndefined, field.getSourceLocation(), message));
        }

    }
}
