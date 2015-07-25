package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

public class ScalarLeafs extends AbstractRule {

    private SchemaUtil schemaUtil = new SchemaUtil();

    public ScalarLeafs(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        if (schemaUtil.isLeafType(type)) {
            if (field.getSelectionSet() != null) {
                addError(new ValidationError(ValidationErrorType.SubSelectionNotAllowed));
            }
        } else {
            if (field.getSelectionSet() == null) {
                addError(new ValidationError(ValidationErrorType.SubSelectionRequired));
            }
        }
    }
}
