package graphql.validation.rules;

import graphql.Internal;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.normalized.FieldCollectorNormalizedQuery;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A GraphQL document is only valid if each subscription operation has exactly one root field
 * https://spec.graphql.org/June2018/#sec-Single-root-field
 */
@Internal
public class SingleSubscriptionFieldOnly extends AbstractRule {

    public SingleSubscriptionFieldOnly(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        super.checkOperationDefinition(operationDefinition);

        // skip validation for non subscription operations
        if (!isSubscriptionOperation(operationDefinition)) {
            return;
        }

        List<Selection> selections = operationDefinition.getSelectionSet().getSelections();
        if (selections.size() > 1) {
            String operationName = operationDefinition.getName();
            addError(ValidationErrorType.MoreThanOneSubscriptionField, operationDefinition.getSourceLocation(), moreThanOneSubscriptionMessage(operationName));
        }
    }

    private boolean isSubscriptionOperation(OperationDefinition operationDefinition) {
        return operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION;
    }

    static String moreThanOneSubscriptionMessage(String name) {
        return String.format("Subscription operation '%s' has more than one root field, when a subscription operation must have exactly one root field.", name);
    }
}
