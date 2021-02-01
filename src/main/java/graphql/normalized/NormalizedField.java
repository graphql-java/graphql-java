package graphql.normalized;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.Internal;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

@Internal
// Mutable Children!
public class NormalizedField {
    private final String alias;
    private final Map<String, Object> arguments;
    private final GraphQLObjectType objectType;
    private final GraphQLFieldDefinition fieldDefinition;
    private Map<String, Map<GraphQLObjectType, NormalizedField>> children;
    private final boolean isConditional;
    private final int level;
    private NormalizedField parent;


    private NormalizedField(Builder builder) {
        this.alias = builder.alias;
        this.arguments = builder.arguments;
        this.objectType = builder.objectType;
        this.fieldDefinition = assertNotNull(builder.fieldDefinition);
        this.children = builder.children;
        this.level = builder.level;
        this.parent = builder.parent;
        // can be null for the top level fields
        if (parent == null) {
            this.isConditional = false;
        } else {
            GraphQLUnmodifiedType parentType = GraphQLTypeUtil.unwrapAll(parent.getFieldDefinition().getType());
            this.isConditional = parentType != this.objectType;
        }
    }

    /**
     * All merged fields have the same name.
     * <p>
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of of the merged fields.
     */
    public String getName() {
        return getFieldDefinition().getName();
    }

    /**
     * Returns the key of this MergedFieldWithType for the overall result.
     * This is either an alias or the FieldWTC name.
     *
     * @return the key for this MergedFieldWithType.
     */
    public String getResultKey() {
        if (alias != null) {
            return alias;
        }
        return getName();
    }

    public String getAlias() {
        return alias;
    }

    public boolean isConditional() {
        return isConditional;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }


    public static Builder newNormalizedField() {
        return new Builder();
    }


    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }


    public NormalizedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public GraphQLObjectType getObjectType() {
        return objectType;
    }

    public String printDetails() {

        StringBuilder result = new StringBuilder();
        if (getAlias() != null) {
            result.append(getAlias()).append(": ");
        }
        return result + objectType.getName() + "." + fieldDefinition.getName() + ": " + simplePrint(fieldDefinition.getType()) +
                " (conditional: " + this.isConditional + ")";
    }

    public String print() {
        StringBuilder result = new StringBuilder();
        result.append("(");
        if (getAlias() != null) {
            result.append(getAlias()).append(":");
        }
        return result + objectType.getName() + "." + fieldDefinition.getName() + ")";
    }

    public String printFullPath() {
        StringBuilder result = new StringBuilder();
        NormalizedField cur = this;
        while (cur != null) {
            if (result.length() > 0) {
                result.insert(0, "/");
            }
            result.insert(0, cur.print());
            cur = cur.getParent();
        }
        return result.toString();
    }

    public List<String> getListOfResultKeys() {
        LinkedList<String> list = new LinkedList<>();
        NormalizedField current = this;
        while (current != null) {
            list.addFirst(current.getResultKey());
            current = current.parent;
        }
        return list;
    }

    public List<NormalizedField> getChildren() {
        ImmutableList.Builder<NormalizedField> builder = ImmutableList.builder();
        for (String resultKey : children.keySet()) {
            builder.addAll(children.get(resultKey).values());
        }
        return builder.build();
    }

    // returns mutable map
    public Map<String, Map<GraphQLObjectType, NormalizedField>> getChildrenAsMap() {
        return children;
    }

    public Collection<NormalizedField> getChildrenForResultKey(String resultKey) {
        if (children.containsKey(resultKey)) {
            return children.get(resultKey).values();
        }
        return Collections.emptyList();
    }

    public void replaceChildren(Map<String, Map<GraphQLObjectType, NormalizedField>> newChildren) {
        this.children = newChildren;
        for (String resultKey : newChildren.keySet()) {
            newChildren.get(resultKey).forEach((graphQLObjectType, normalizedField) -> {
                normalizedField.replaceParent(this);
            });
        }
    }

    public GraphQLOutputType getFieldType() {
        return getFieldDefinition().getType();
    }

    public GraphQLCompositeType getUnwrappedFieldType() {
        return (GraphQLCompositeType) unwrapAll(getFieldDefinition().getType());
    }

    public int getLevel() {
        return level;
    }

    public NormalizedField getParent() {
        return parent;
    }

    public void replaceParent(NormalizedField newParent) {
        this.parent = newParent;
    }

    public boolean isIntrospectionField() {
        return getFieldDefinition().getName().startsWith("__") || getObjectType().getName().startsWith("__");
    }

    @Override
    public String toString() {
        return "NormalizedField{" +
                objectType.getName() + "." + fieldDefinition.getName() +
                ", alias=" + alias +
                ", level=" + level +
                ", conditional=" + isConditional +
                ", children=" + getChildren().stream().map(NormalizedField::toString).collect(Collectors.joining("\n")) +
                '}';
    }

    public List<NormalizedField> getChildren(int includingRelativeLevel) {
        List<NormalizedField> result = new ArrayList<>();
        Assert.assertTrue(includingRelativeLevel >= 1, () -> "relative level must be >= 1");

        this.getChildren().forEach(child -> {
            traverseImpl(child, result::add, 1, includingRelativeLevel);
        });
        return result;
    }


    public void traverseSubTree(Consumer<NormalizedField> consumer) {
        this.getChildren().forEach(child -> {
            traverseImpl(child, consumer, 1, Integer.MAX_VALUE);
        });
    }

    private void traverseImpl(NormalizedField root,
                              Consumer<NormalizedField> consumer,
                              int curRelativeLevel,
                              int abortAfter) {
        if (curRelativeLevel > abortAfter) {
            return;
        }
        consumer.accept(root);
        root.getChildren().forEach(child -> {
            traverseImpl(child, consumer, curRelativeLevel + 1, abortAfter);
        });
    }

    public static class Builder {
        private GraphQLObjectType objectType;
        private GraphQLFieldDefinition fieldDefinition;
        private Map<String, Map<GraphQLObjectType, NormalizedField>> children = new LinkedHashMap<>();
        private int level;
        private NormalizedField parent;
        private String alias;
        private Map<String, Object> arguments = Collections.emptyMap();

        private Builder() {

        }

        private Builder(NormalizedField existing) {
            this.alias = existing.alias;
            this.arguments = existing.arguments;
            this.objectType = existing.getObjectType();
            this.fieldDefinition = existing.getFieldDefinition();
            this.children = new LinkedHashMap<>(existing.getChildrenAsMap());
            this.level = existing.getLevel();
            this.parent = existing.getParent();
        }

        public Builder objectType(GraphQLObjectType objectType) {
            this.objectType = objectType;
            return this;
        }


        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments == null ? Collections.emptyMap() : arguments;
            return this;
        }


        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder children(List<NormalizedField> children) {
            this.children.clear();
            for (NormalizedField child : children) {
                this.children.computeIfAbsent(child.getResultKey(), ignored -> new LinkedHashMap<>());
                this.children.get(child.getResultKey()).put(child.getObjectType(), child);
            }
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder parent(NormalizedField parent) {
            this.parent = parent;
            return this;
        }

        public NormalizedField build() {
            return new NormalizedField(this);
        }


    }

}
