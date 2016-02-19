package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

/**
 * <p>ScalarLeafs class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ScalarLeafs extends AbstractRule {

    private SchemaUtil schemaUtil = new SchemaUtil();

    /**
     * <p>Constructor for ScalarLeafs.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public ScalarLeafs(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        if (schemaUtil.isLeafType(type)) {
            if (field.getSelectionSet() != null) {
                String message = String.format("Sub selection not allowed on leaf type %s", type.getName());
                addError(new ValidationError(ValidationErrorType.SubSelectionNotAllowed, field.getSourceLocation(), message));
            }
        } else {
            if (field.getSelectionSet() == null) {
                String message = String.format("Sub selection required for type %s", type.getName());
                addError(new ValidationError(ValidationErrorType.SubSelectionRequired, field.getSourceLocation(), message));
            }
        }
    }
}
