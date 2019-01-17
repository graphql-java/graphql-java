package graphql.execution.directives;

import graphql.Assert;
import graphql.PublicApi;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.DirectivesContainer;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLDirective;

import java.util.Map;
import java.util.stream.Collectors;

import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

/**
 * This value class holds information about the hierarchical directives on a field, what held them
 * and how far away from the field was those directives.
 */
@PublicApi
public class FieldDirectivesInfoImpl implements FieldDirectivesInfo {
    private final DirectivesContainer directivesContainer;
    private final Map<String, GraphQLDirective> directives;
    private final int distance;
    private final DirectiveLocation directiveLocation;

    FieldDirectivesInfoImpl(DirectivesContainer directivesContainer, int distance, Map<String, GraphQLDirective> directives) {
        this.directivesContainer = directivesContainer;
        this.distance = distance;
        this.directives = directives;
        this.directiveLocation = mkLocation(directivesContainer);
    }

    @Override
    public FieldDirectivesInfo restrictTo(String directiveName) {
        Map<String, GraphQLDirective> collect = directives.entrySet().stream()
                .filter(entry -> directiveName.equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new FieldDirectivesInfoImpl(this.directivesContainer, this.distance, collect);
    }


    private DirectiveLocation mkLocation(DirectivesContainer container) {
        if (container instanceof Field) {
            return DirectiveLocation.FIELD;
        }
        if (container instanceof FragmentDefinition) {
            return DirectiveLocation.FRAGMENT_DEFINITION;
        }
        if (container instanceof FragmentSpread) {
            return DirectiveLocation.FRAGMENT_SPREAD;
        }
        if (container instanceof InlineFragment) {
            return DirectiveLocation.INLINE_FRAGMENT;
        }
        if (container instanceof OperationDefinition) {
            OperationDefinition op = (OperationDefinition) container;
            if (op.getOperation() == QUERY) {
                return DirectiveLocation.QUERY;
            }
            if (op.getOperation() == MUTATION) {
                return DirectiveLocation.MUTATION;
            }
            if (op.getOperation() == SUBSCRIPTION) {
                return DirectiveLocation.SUBSCRIPTION;
            }
        }
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public DirectivesContainer getDirectivesContainer() {
        return directivesContainer;
    }

    @Override
    public Map<String, GraphQLDirective> getDirectives() {
        return directives;
    }

    @Override
    public int getDistance() {
        return distance;
    }

    @Override
    public DirectiveLocation getDirectiveLocation() {
        return directiveLocation;
    }

    @Override
    public String toString() {
        return "FieldDirectivesInfoImpl{" +
                ", distance=" + distance +
                ", directiveLocation=" + directiveLocation +
                ", directives=" + directives +
                '}';
    }

    @Override
    public int compareTo(FieldDirectivesInfo that) {
        if (this == that) {
            return 0;
        }
        int rc = this.distance - that.getDistance();
        if (rc == 0) {
            rc = this.directivesContainer.getName().compareTo(that.getDirectivesContainer().getName());
        }
        return rc;
    }
}
