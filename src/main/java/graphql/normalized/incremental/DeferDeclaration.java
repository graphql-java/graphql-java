package graphql.normalized.incremental;

import graphql.Internal;

import javax.annotation.Nullable;

/**
 * TODO: Javadoc
 */
@Internal
public class DeferDeclaration {
    private final String label;
    private final String targetType;

    public DeferDeclaration(@Nullable String label, @Nullable String targetType) {
        this.label = label;
        this.targetType = targetType;
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
}
