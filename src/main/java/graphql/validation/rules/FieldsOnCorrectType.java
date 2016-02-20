package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.*;

/**
 * <p>FieldsOnCorrectType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class FieldsOnCorrectType extends AbstractRule {


    /**
     * <p>Constructor for FieldsOnCorrectType.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public FieldsOnCorrectType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    /** {@inheritDoc} */
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
