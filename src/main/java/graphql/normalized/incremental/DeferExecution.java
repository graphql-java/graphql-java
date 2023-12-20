package graphql.normalized.incremental;

import graphql.ExperimentalApi;
import graphql.language.TypeName;

import javax.annotation.Nullable;

/**
 * Represents the defer execution aspect of a query field
 */
@ExperimentalApi
public class DeferExecution {
    private final String label;
    private final TypeName targetType;

    public DeferExecution(@Nullable String label, @Nullable TypeName targetType) {
        this.label = label;
        this.targetType = targetType;
    }

    /**
     * @return the label associated with this defer execution
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * @return the {@link TypeName} of the type that is the target of the defer execution
     */
    @Nullable
    public TypeName getTargetType() {
        return targetType;
    }
}
