package graphql.normalized.incremental;

import graphql.Assert;
import graphql.Internal;
import graphql.schema.GraphQLObjectType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO: Javadoc
 */
@Internal
public class DeferDeclaration {
    private final String label;

    private final LinkedHashSet<GraphQLObjectType> objectTypes;

    public DeferDeclaration(@Nullable String label, @Nonnull Set<GraphQLObjectType> objectTypes) {
        this.label = label;

        Assert.assertNotEmpty(objectTypes, () -> "A defer declaration must be associated with at least 1 GraphQL object");

        this.objectTypes = new LinkedHashSet<>(objectTypes);
    }

    /**
     * @return the label associated with this defer declaration
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    public void addObjectTypes(Collection<GraphQLObjectType> objectTypes) {
        this.objectTypes.addAll(objectTypes);
    }

    /**
     * TODO Javadoc
     */
    public Set<GraphQLObjectType> getObjectTypes() {
        return objectTypes;
    }

    public Set<String> getObjectTypeNames() {
        return objectTypes.stream()
                .map(GraphQLObjectType::getName)
                .collect(Collectors.toSet());
    }
}
