package graphql.validation.rules;


import graphql.Internal;
import graphql.language.Field;
import graphql.schema.GraphQLOutputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.schema.GraphQLTypeUtil.isLeaf;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.validation.ValidationErrorType.SubselectionNotAllowed;
import static graphql.validation.ValidationErrorType.SubselectionRequired;

@Internal
public class ScalarLeaves extends AbstractRule {

    public ScalarLeaves(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkField(Field field) {
        GraphQLOutputType type = getValidationContext().getOutputType();
        if (type == null) return;
        if (isLeaf(type)) {
            if (field.getSelectionSet() != null) {
                String message = i18n(SubselectionNotAllowed, "ScalarLeaves.subselectionOnLeaf", simplePrint(type), field.getName());
                addError(SubselectionNotAllowed, field.getSourceLocation(), message);
            }
        } else {
            if (field.getSelectionSet() == null) {
                String message = i18n(SubselectionRequired, "ScalarLeaves.subselectionRequired", simplePrint(type), field.getName());
                addError(SubselectionRequired, field.getSourceLocation(), message);
            }
        }
    }
}
