package graphql.validation.rules;

import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.schema.GraphQLObjectType;
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
    private final FieldCollector fieldCollector = new FieldCollector();
    public SubscriptionUniqueRootField(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDef) {
        if (operationDef.getOperation() == SUBSCRIPTION) {

            GraphQLObjectType subscriptionType = getValidationContext().getSchema().getSubscriptionType();

            FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                    .schema(getValidationContext().getSchema())
                    .fragments(NodeUtil.getFragmentsByName(getValidationContext().getDocument()))
                    .variables(CoercedVariables.emptyVariables().toMap())
                    .objectType(subscriptionType)
                    .graphQLContext(getValidationContext().getGraphQLContext())
                    .build();

            MergedSelectionSet fields = fieldCollector.collectFields(collectorParameters, operationDef.getSelectionSet());
            List<Selection> subscriptionSelections = operationDef.getSelectionSet().getSelections();

            if (fields.size() > 1) {
                String message = i18n(SubscriptionMultipleRootFields, "SubscriptionUniqueRootField.multipleRootFields", operationDef.getName());
                addError(SubscriptionMultipleRootFields, operationDef.getSourceLocation(), message);
            } else { // Only one item in selection set, size == 1

                MergedField mergedField  = fields.getSubFieldsList().get(0);


                if (isIntrospectionField(mergedField)) {
                    String message = i18n(SubscriptionIntrospectionRootField, "SubscriptionIntrospectionRootField.introspectionRootField", operationDef.getName(), mergedField.getName());
                    addError(SubscriptionIntrospectionRootField, mergedField.getSingleField().getSourceLocation(), message);
                }
            }
        }
    }

    private boolean isIntrospectionField(MergedField field) {
           return field.getName().startsWith("__");
    }
}
