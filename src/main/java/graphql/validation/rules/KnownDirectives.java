package graphql.validation.rules;


import graphql.GraphQLException;
import graphql.introspection.Introspection.DirectiveLocation;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static graphql.introspection.Introspection.DirectiveLocation.MUTATION;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.schema.GraphQLDirective;
import graphql.util.SimpleTraverserContext;
import graphql.util.TraversalControl;
import static graphql.util.TraversalControl.QUIT;
import graphql.util.TraverserContext;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.List;

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

    private boolean hasInvalidLocation(GraphQLDirective directive, Node ancestor) {
        Context context = new Context(ancestor,directive);
        ancestor.accept(context,LOCATION_VISITOR);
        return context.isValid();
    }

    private final static LocationVisitor LOCATION_VISITOR = new LocationVisitor();

    private class Context extends SimpleTraverserContext<Node> {
        final GraphQLDirective directive;

        Context(Node node, GraphQLDirective directive) {
            super(node);
            this.directive = directive;
        }

        @Override
        public Object getInitialData() {
            return directive;
        }

        Boolean isValid() {
            return (Boolean)getResult();
        }
    }

    private static class LocationVisitor extends NodeVisitorStub {
        @Override
        public TraversalControl visitField(Field node, TraverserContext<Node> context) {
            return setAndQuit(context, FIELD);
        }

        @Override
        public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
            return setAndQuit(context, FRAGMENT_SPREAD);
        }

        @Override
        public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
            return setAndQuit(context, FRAGMENT_DEFINITION);
        }

        @Override
        public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
            return setAndQuit(context, INLINE_FRAGMENT);
        }

        @Override
        public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
            return setAndQuit(context, Operation.QUERY.equals(node.getOperation()) ? QUERY : MUTATION);
        }

        @Override
        protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
            context.setResult(true);
            return QUIT;
        }

        private boolean has(GraphQLDirective directive, DirectiveLocation thing) {
            return directive.validLocations().contains(thing);
        }

        private TraversalControl setAndQuit(TraverserContext<Node> context, DirectiveLocation location) {
            context.setResult(isInvalidLocation(context,location));
            return QUIT;
        }

        private GraphQLDirective getDirective(TraverserContext<Node> context) {
            return (GraphQLDirective)context.getInitialData();
        }

        @SuppressWarnings("deprecation") // the suppression stands because its deprecated but still in graphql spec
        private boolean legacyIsOnDirective(DirectiveLocation location, GraphQLDirective directive) {
            switch (location) {
                case FIELD:
                    return directive.isOnField();

                case FRAGMENT_SPREAD:
                case FRAGMENT_DEFINITION:
                case INLINE_FRAGMENT:
                    return directive.isOnFragment();

                case QUERY:
                case MUTATION:
                    return directive.isOnOperation();

                default:
                    throw new GraphQLException("Legacy behaviour did not expect location " + location.toString());
            }
        }

        private boolean isInvalidLocation(TraverserContext<Node> context, DirectiveLocation location) {
            GraphQLDirective directive = getDirective(context);
            return !(has(directive,location) || legacyIsOnDirective(location, directive));

        }
    }
}
