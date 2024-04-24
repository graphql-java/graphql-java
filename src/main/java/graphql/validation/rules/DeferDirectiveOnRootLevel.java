package graphql.validation.rules;

import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLObjectType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static graphql.validation.ValidationErrorType.MisplacedDirective;

/**
 * Defer and stream directives are used on valid root field
 *
 * A GraphQL document is only valid if defer directives are not used on root mutation or subscription types.
 *
 * See proposed spec:<a href="https://github.com/graphql/graphql-spec/pull/742">spec/Section 5 -- Validation.md ### Defer And Stream Directives Are Used On Valid Root Field</a>
 */
@ExperimentalApi
public class DeferDirectiveOnRootLevel extends AbstractRule {
    private Set<OperationDefinition.Operation> invalidOperations = new LinkedHashSet(Arrays.asList(OperationDefinition.Operation.MUTATION, OperationDefinition.Operation.SUBSCRIPTION));
    public DeferDirectiveOnRootLevel(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        this.setVisitFragmentSpreads(true);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        // ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT must be true
        if (!isExperimentalApiKeyEnabled(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT)) {
            return;
        }

        if (!Directives.DeferDirective.getName().equals(directive.getName())) {
            return;
        }
        GraphQLObjectType mutationType = getValidationContext().getSchema().getMutationType();
        GraphQLObjectType subscriptionType = getValidationContext().getSchema().getSubscriptionType();
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (mutationType != null && parentType != null && parentType.getName().equals(mutationType.getName())){
            String message = i18n(MisplacedDirective, "DeferDirective.notAllowedOperationRootLevelMutation", parentType.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        } else if (subscriptionType != null && parentType != null && parentType.getName().equals(subscriptionType.getName())) {
            String message = i18n(MisplacedDirective, "DeferDirective.notAllowedOperationRootLevelSubscription", parentType.getName());
            addError(MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

}
