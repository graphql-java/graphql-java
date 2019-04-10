package graphql.validation.rules;

import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.LoneAnonymousOperationViolation;

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
                message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.withOthers");
            }
        } else {
            if (hasAnonymousOp) {
                message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.namedOperation", name);
            }
        }
        count++;
        if (message != null) {
            addError(LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
        }
    }

    @Override
    public void documentFinished(Document document) {
        super.documentFinished(document);
        hasAnonymousOp = false;
    }
}
