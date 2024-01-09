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
    private final String targetType;
    private final Set<GraphQLObjectType> possibleTypes;

    public DeferExecution(@Nullable String label, @Nullable String targetType, Set<GraphQLObjectType> possibleTypes) {
        this.label = label;
        this.targetType = targetType;
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
     * @return the name of the type that is the target of the defer declaration
     */
    @Nullable
    public String getTargetType() {
        return targetType;
    }

    /**
     * TODO Javadoc
     */
    public Set<GraphQLObjectType> getPossibleTypes() {
        return possibleTypes;
    }
}
