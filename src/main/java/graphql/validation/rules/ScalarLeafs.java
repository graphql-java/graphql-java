package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SchemaUtil;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

public class ScalarLeafs extends AbstractRule {

    private final SchemaUtil schemaUtil = new SchemaUtil();

    public ScalarLeafs(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        if (schemaUtil.isLeafType(type)) {
            if (field.getSelectionSet() != null) {
                String message = String.format("Sub selection not allowed on leaf type %s of field %s", type.getName(), field.getName());
                addError(ValidationErrorType.SubSelectionNotAllowed, field.getSourceLocation(), message);
            }
        } else {
            if (field.getSelectionSet() == null) {
                String message = String.format("Sub selection required for type %s of field %s", type.getName(), field.getName());
                addError(ValidationErrorType.SubSelectionRequired, field.getSourceLocation(), message);
            }
        }
    }
}
