package graphql.normalized.incremental;

import graphql.Internal;
import graphql.schema.GraphQLObjectType;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * TODO: Javadoc
 */
@Internal
public class DeferExecution {
    private final String label;
    private final Set<GraphQLObjectType> possibleTypes;

    public DeferExecution(@Nullable String label, Set<GraphQLObjectType> possibleTypes) {
        this.label = label;
        this.possibleTypes = possibleTypes;
    }

    /**
     * @return the label associated with this defer declaration
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * TODO Javadoc
     */
    public Set<GraphQLObjectType> getPossibleTypes() {
        return possibleTypes;
    }
}
