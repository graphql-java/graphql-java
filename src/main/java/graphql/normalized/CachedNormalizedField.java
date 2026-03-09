package graphql.normalized;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.language.Argument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static java.util.stream.Collectors.joining;

/**
 * A cached, variable-independent representation of a normalized field.
 * <p>
 * Unlike {@link ExecutableNormalizedField}, this class does NOT evaluate skip/include directives
 * or coerce argument values. It captures the full query structure with unevaluated
 * {@link FieldInclusionCondition}s, making it safe to cache and reuse across requests
 * with different variable values.
 * <p>
 * The tree of CachedNormalizedFields is intentionally NOT merged across type conditions.
 * Fields that would be merged in the final ENF tree (e.g., [Dog].name + [Cat].name → [Dog,Cat].name)
 * are kept separate here, each with their own inclusion condition. Merging is deferred to
 * materialization time when the conditions can be evaluated.
 * <p>
 * To produce an {@link ExecutableNormalizedOperation} for execution, use
 * {@link CachedOperationMaterializer#materialize(CachedNormalizedOperation, java.util.Map, graphql.schema.GraphQLSchema, graphql.GraphQLContext, java.util.Locale)}.
 */
@Internal
public class CachedNormalizedField {

    private final String fieldName;
    private final String alias;
    private final ImmutableList<Argument> astArguments;
    private final LinkedHashSet<String> objectTypeNames;
    private final int level;
    private final FieldInclusionCondition inclusionCondition;

    // Mutable for construction convenience (parent-child built incrementally)
    private CachedNormalizedField parent;
    private final ArrayList<CachedNormalizedField> children;

    private CachedNormalizedField(Builder builder) {
        this.fieldName = assertNotNull(builder.fieldName);
        this.alias = builder.alias;
        this.astArguments = builder.astArguments;
        this.objectTypeNames = builder.objectTypeNames;
        this.level = builder.level;
        this.inclusionCondition = builder.inclusionCondition;
        this.parent = builder.parent;
        this.children = new ArrayList<>();
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAlias() {
        return alias;
    }

    public String getResultKey() {
        return alias != null ? alias : fieldName;
    }

    public ImmutableList<Argument> getAstArguments() {
        return astArguments;
    }

    public Set<String> getObjectTypeNames() {
        return objectTypeNames;
    }

    public String getSingleObjectTypeName() {
        return objectTypeNames.iterator().next();
    }

    public int getLevel() {
        return level;
    }

    public FieldInclusionCondition getInclusionCondition() {
        return inclusionCondition;
    }

    public CachedNormalizedField getParent() {
        return parent;
    }

    public List<CachedNormalizedField> getChildren() {
        return children;
    }

    @Internal
    public void addChild(CachedNormalizedField child) {
        this.children.add(child);
    }

    @Internal
    void setParent(CachedNormalizedField parent) {
        this.parent = parent;
    }

    public String printDetails() {
        StringBuilder result = new StringBuilder();
        if (alias != null) {
            result.append(alias).append(": ");
        }
        if (objectTypeNames.size() == 1) {
            result.append(objectTypeNames.iterator().next());
        } else {
            result.append(objectTypeNames);
        }
        result.append(".").append(fieldName);
        return result.toString();
    }

    @Override
    public String toString() {
        return "CachedNormalizedField{" +
                printDetails() +
                ", condition=" + inclusionCondition +
                ", children=" + children.stream().map(CachedNormalizedField::toString).collect(joining("\n")) +
                '}';
    }

    public static Builder newCachedField() {
        return new Builder();
    }

    public static class Builder {
        private String fieldName;
        private String alias;
        private ImmutableList<Argument> astArguments = ImmutableList.of();
        private LinkedHashSet<String> objectTypeNames = new LinkedHashSet<>();
        private int level;
        private CachedNormalizedField parent;
        private FieldInclusionCondition inclusionCondition = FieldInclusionCondition.ALWAYS;

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder astArguments(List<Argument> astArguments) {
            this.astArguments = ImmutableList.copyOf(astArguments);
            return this;
        }

        public Builder objectTypeNames(Set<String> objectTypeNames) {
            this.objectTypeNames = new LinkedHashSet<>(objectTypeNames);
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder parent(CachedNormalizedField parent) {
            this.parent = parent;
            return this;
        }

        public Builder inclusionCondition(FieldInclusionCondition condition) {
            this.inclusionCondition = condition;
            return this;
        }

        public CachedNormalizedField build() {
            return new CachedNormalizedField(this);
        }
    }
}
