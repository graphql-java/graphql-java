package graphql.validation.rules;


import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.schema.GraphQLTypeUtil.isLeaf;
import static graphql.validation.ValidationErrorType.SubSelectionNotAllowed;
import static graphql.validation.ValidationErrorType.SubSelectionRequired;

public class ScalarLeafs extends AbstractRule {

    public ScalarLeafs(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        if (isLeaf(type)) {
            if (field.getSelectionSet() != null) {
                String message = i18n(SubSelectionNotAllowed, "ScalarLeafs.subSelectionOnLeaf", type.getName(), field.getName());
                addError(SubSelectionNotAllowed, field.getSourceLocation(), message);
            }
        } else {
            if (field.getSelectionSet() == null) {
                String message = i18n(SubSelectionRequired, "ScalarLeafs.subSelectionRequired", type.getName(), field.getName());
                addError(SubSelectionRequired, field.getSourceLocation(), message);
            }
        }
    }
}
