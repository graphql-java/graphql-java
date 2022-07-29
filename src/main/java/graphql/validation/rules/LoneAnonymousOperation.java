package graphql.validation.rules;

import graphql.Internal;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import static graphql.validation.ValidationErrorType.LoneAnonymousOperationViolation;

@Internal
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

        if (name == null) {
            hasAnonymousOp = true;
            if (count > 0) {
                String message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.withOthers");
                addError(ValidationErrorType.LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
            }
        } else {
            if (hasAnonymousOp) {
                String message = i18n(LoneAnonymousOperationViolation, "LoneAnonymousOperation.namedOperation", name);
                addError(ValidationErrorType.LoneAnonymousOperationViolation, operationDefinition.getSourceLocation(), message);
            }
        }
        count++;
    }

    @Override
    public void documentFinished(Document document) {
        super.documentFinished(document);
        hasAnonymousOp = false;
    }
}
