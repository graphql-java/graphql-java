package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.FieldUndefined;

public class FieldsOnCorrectType extends AbstractRule {


    public FieldsOnCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkField(Field field) {
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        // this means the parent type is not a CompositeType, which is an error handled elsewhere
        if (parentType == null) return;
        GraphQLFieldDefinition fieldDef = getValidationContext().getFieldDef();
        if (fieldDef == null) {
            String message = i18n(FieldUndefined, "FieldsOnCorrectType.unknownField", field.getName(), parentType.getName());
            addError(FieldUndefined, field.getSourceLocation(), message);
        }

    }
}
