package graphql.validation.rules;

import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.RawVariables;
import graphql.execution.ValuesResolver;
import graphql.language.Directive;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.List;

import static graphql.Directives.INCLUDE_DIRECTIVE_DEFINITION;
import static graphql.Directives.SKIP_DIRECTIVE_DEFINITION;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static graphql.validation.ValidationErrorType.SubscriptionIntrospectionRootField;
import static graphql.validation.ValidationErrorType.SubscriptionMultipleRootFields;
import static graphql.validation.ValidationErrorType.ForbidSkipAndIncludeOnSubscriptionRoot;


/**
 * A subscription operation must only have one root field
 * A subscription operation's single root field must not be an introspection field
 * https://spec.graphql.org/draft/#sec-Single-root-field
 *
 * A subscription operation's root field must not have neither @skip nor @include directives
 */
@Internal
public class SubscriptionRootField extends AbstractRule {
    private final FieldCollector fieldCollector = new FieldCollector();
    public SubscriptionRootField(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDef) {
        if (operationDef.getOperation() == SUBSCRIPTION) {

            GraphQLObjectType subscriptionType = getValidationContext().getSchema().getSubscriptionType();

            // This coercion takes into account default values for variables
            List<VariableDefinition> variableDefinitions = operationDef.getVariableDefinitions();
            CoercedVariables coercedVariableValues = ValuesResolver.coerceVariableValues(
                    getValidationContext().getSchema(),
                    variableDefinitions,
                    RawVariables.emptyVariables(),
                    getValidationContext().getGraphQLContext(),
                    getValidationContext().getI18n().getLocale());

            FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                    .schema(getValidationContext().getSchema())
                    .fragments(NodeUtil.getFragmentsByName(getValidationContext().getDocument()))
                    .variables(coercedVariableValues.toMap())
                    .objectType(subscriptionType)
                    .graphQLContext(getValidationContext().getGraphQLContext())
                    .build();

            MergedSelectionSet fields = fieldCollector.collectFields(collectorParameters, operationDef.getSelectionSet());

            if (fields.size() > 1) {
                String message = i18n(SubscriptionMultipleRootFields, "SubscriptionUniqueRootField.multipleRootFields", operationDef.getName());
                addError(SubscriptionMultipleRootFields, operationDef.getSourceLocation(), message);
            } else { // Only one item in selection set, size == 1

                MergedField mergedField  = fields.getSubFieldsList().get(0);

                if (isIntrospectionField(mergedField)) {
                    String message = i18n(SubscriptionIntrospectionRootField, "SubscriptionIntrospectionRootField.introspectionRootField", operationDef.getName(), mergedField.getName());
                    addError(SubscriptionIntrospectionRootField, mergedField.getSingleField().getSourceLocation(), message);
                }

                if (hasSkipOrIncludeDirectives(mergedField)) {
                    String message = i18n(ForbidSkipAndIncludeOnSubscriptionRoot, "SubscriptionRootField.forbidSkipAndIncludeOnSubscriptionRoot", operationDef.getName(), mergedField.getName());
                    addError(ForbidSkipAndIncludeOnSubscriptionRoot, mergedField.getSingleField().getSourceLocation(), message);
                }
            }
        }
    }

    private boolean isIntrospectionField(MergedField field) {
           return field.getName().startsWith("__");
    }

    private boolean hasSkipOrIncludeDirectives(MergedField field) {
        List<Directive> directives = field.getSingleField().getDirectives();
        for (Directive directive : directives) {
            if (directive.getName().equals(SKIP_DIRECTIVE_DEFINITION.getName()) || directive.getName().equals(INCLUDE_DIRECTIVE_DEFINITION.getName())) {
                return true;
            }
        }
        return false;
    }
}
