package graphql.validation.rules;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.List;

import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static graphql.validation.ValidationErrorType.SubscriptionIntrospectionRootField;
import static graphql.validation.ValidationErrorType.SubscriptionMultipleRootFields;


/**
 * A subscription operation must only have one root field
 * A subscription operation's single root field must not be an introspection field
 * https://spec.graphql.org/draft/#sec-Single-root-field
 */
@Internal
public class SubscriptionUniqueRootField extends AbstractRule {
    public SubscriptionUniqueRootField(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDef) {
        if (operationDef.getOperation() == SUBSCRIPTION) {
            List<Selection> subscriptionSelections = operationDef.getSelectionSet().getSelections();

            if (subscriptionSelections.size() > 1) {
                String message = i18n(SubscriptionMultipleRootFields, "SubscriptionUniqueRootField.multipleRootFields", operationDef.getName());
                addError(SubscriptionMultipleRootFields, operationDef.getSourceLocation(), message);
            } else { // Only one item in selection set, size == 1
                Selection rootSelection = subscriptionSelections.get(0);

                if (isIntrospectionField(rootSelection)) {
                    String message = i18n(SubscriptionIntrospectionRootField, "SubscriptionIntrospectionRootField.introspectionRootField", operationDef.getName(), ((Field) rootSelection).getName());
                    addError(SubscriptionIntrospectionRootField, rootSelection.getSourceLocation(), message);
                } else if (rootSelection instanceof FragmentSpread) {
                    // If the only item in selection set is a fragment, inspect the fragment.
                    String fragmentName = ((FragmentSpread) rootSelection).getName();
                    FragmentDefinition fragmentDef = getValidationContext().getFragment(fragmentName);
                    List<Selection> fragmentSelections = fragmentDef.getSelectionSet().getSelections();

                    if (fragmentSelections.size() > 1) {
                        String message = i18n(SubscriptionMultipleRootFields, "SubscriptionUniqueRootField.multipleRootFieldsWithFragment", operationDef.getName());
                        addError(SubscriptionMultipleRootFields, rootSelection.getSourceLocation(), message);
                    } else if (isIntrospectionField(fragmentSelections.get(0))) {
                        String message = i18n(SubscriptionIntrospectionRootField, "SubscriptionIntrospectionRootField.introspectionRootFieldWithFragment", operationDef.getName(), ((Field) fragmentSelections.get(0)).getName());
                        addError(SubscriptionIntrospectionRootField, rootSelection.getSourceLocation(), message);
                    }
                }
            }
        }
    }

    private boolean isIntrospectionField(Selection selection) {
        return selection instanceof Field && ((Field) selection).getName().startsWith("__");
    }
}
