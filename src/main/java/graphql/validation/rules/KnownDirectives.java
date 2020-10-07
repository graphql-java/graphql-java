package graphql.validation.rules;


import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLDirective;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.List;
import java.util.EnumSet;

@Internal
public class KnownDirectives extends AbstractRule {


    public KnownDirectives(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkDirective(Directive directive, List<Node> ancestors) {
        GraphQLDirective graphQLDirective = getValidationContext().getSchema().getDirective(directive.getName());
        if (graphQLDirective == null) {
            String message = String.format("Unknown directive %s", directive.getName());
            addError(ValidationErrorType.UnknownDirective, directive.getSourceLocation(), message);
            return;
        }

        Node ancestor = ancestors.get(ancestors.size() - 1);
        if (hasInvalidLocation(graphQLDirective, ancestor)) {
            String message = String.format("Directive %s not allowed here", directive.getName());
            addError(ValidationErrorType.MisplacedDirective, directive.getSourceLocation(), message);
        }
    }

    @SuppressWarnings("deprecation") // the suppression stands because its deprecated but still in graphql spec
    private boolean hasInvalidLocation(GraphQLDirective directive, Node ancestor) {
        EnumSet<DirectiveLocation> validLocations = directive.validLocations();
        if (ancestor instanceof OperationDefinition) {
            Operation operation = ((OperationDefinition) ancestor).getOperation();
            if (Operation.QUERY.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.QUERY);
            } else if (Operation.MUTATION.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.MUTATION);
            } else if (Operation.SUBSCRIPTION.equals(operation)) {
                return !validLocations.contains(DirectiveLocation.SUBSCRIPTION);
            }
        } else if (ancestor instanceof Field) {
            return !(validLocations.contains(DirectiveLocation.FIELD));
        } else if (ancestor instanceof FragmentSpread) {
            return !(validLocations.contains(DirectiveLocation.FRAGMENT_SPREAD));
        } else if (ancestor instanceof FragmentDefinition) {
            return !(validLocations.contains(DirectiveLocation.FRAGMENT_DEFINITION));
        } else if (ancestor instanceof InlineFragment) {
            return !(validLocations.contains(DirectiveLocation.INLINE_FRAGMENT));
        } else if (ancestor instanceof VariableDefinition) {
            return !(validLocations.contains(DirectiveLocation.VARIABLE_DEFINITION));
        }
        return true;
    }
}
