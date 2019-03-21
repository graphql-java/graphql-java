package graphql.validation.rules;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

public class LoneAnonymousOperation extends AbstractRule {

    boolean hasAnonymousOp = false;
    int count = 0;

    public LoneAnonymousOperation(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        super.checkOperationDefinition(operationDefinition);
        String name = operationDefinition.getName();
        String message = null;

        if (name == null) {
            hasAnonymousOp = true;
            if (count > 0) {
                message = i18n("LoneAnonymousOperation.withOthers");
            }
        } else {
            if (hasAnonymousOp) {
                message = i18n("LoneAnonymousOperation.namedOperation", name);
            }
        }
        count++;
        if (message != null) {
            addError(ValidationErrorType.LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
        }
    }

    @Override
    public void documentFinished(Document document) {
        super.documentFinished(document);
        hasAnonymousOp = false;
    }
}
