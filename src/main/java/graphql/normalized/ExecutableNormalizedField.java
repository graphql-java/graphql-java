package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.Mutable;
import graphql.collect.ImmutableKit;
import graphql.language.Argument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.util.FpKit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Intentionally Mutable
 */
@Internal
@Mutable
public class ExecutableNormalizedField {
    private final String alias;
    private final ImmutableMap<String, NormalizedInputValue> normalizedArguments;
    private final LinkedHashMap<String, Object> resolvedArguments;
    private final ImmutableList<Argument> astArguments;

    // Mutable List on purpose: it is modified after creation
    private final LinkedHashSet<String> objectTypeNames;
    private final ArrayList<ExecutableNormalizedField> children;
    private ExecutableNormalizedField parent;

    private final String fieldName;
    private final int level;


    private ExecutableNormalizedField(Builder builder) {
        this.alias = builder.alias;
        this.resolvedArguments = builder.resolvedArguments;
        this.normalizedArguments = builder.normalizedArguments;
        this.astArguments = builder.astArguments;
        this.objectTypeNames = builder.objectTypeNames;
        this.fieldName = assertNotNull(builder.fieldName);
        this.children = builder.children;
        this.level = builder.level;
        this.parent = builder.parent;
    }

    public boolean isConditional(GraphQLSchema schema) {
        if (parent == null) {
            return false;
        }
        return objectTypeNames.size() > 1 || unwrapAll(parent.getType(schema)) != getOneObjectType(schema);
    }

    public GraphQLOutputType getType(GraphQLSchema schema) {
        return getOneFieldDefinition(schema).getType();
    }

    public GraphQLFieldDefinition getOneFieldDefinition(GraphQLSchema schema) {
        GraphQLFieldDefinition fieldDefinition;
        GraphQLFieldDefinition introspectionField = resolveIntrospectionField(fieldName, schema);
        if (introspectionField != null) {
            return introspectionField;
        }
        GraphQLObjectType type = (GraphQLObjectType) assertNotNull(schema.getType(objectTypeNames.iterator().next()));
        fieldDefinition = assertNotNull(type.getField(fieldName), () -> String.format("no field %s found for type %s", fieldName, objectTypeNames.iterator().next()));
        return fieldDefinition;
    }

    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLSchema schema) {
        GraphQLFieldDefinition fieldDefinition = resolveIntrospectionField(fieldName, schema);
        if (fieldDefinition != null) {
            return ImmutableList.of(fieldDefinition);
        }
        ImmutableList.Builder<GraphQLFieldDefinition> builder = ImmutableList.builder();
        for (String objectTypeName : objectTypeNames) {
            GraphQLObjectType type = (GraphQLObjectType) assertNotNull(schema.getType(objectTypeName));
            builder.add(assertNotNull(type.getField(fieldName), () -> String.format("no field %s found for type %s", fieldName, objectTypeNames.iterator().next())));
        }
        return builder.build();
    }

    private static GraphQLFieldDefinition resolveIntrospectionField(String fieldName, GraphQLSchema schema) {
        if (fieldName.equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
            return schema.getIntrospectionTypenameFieldDefinition();
        } else {
            if (fieldName.equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                return schema.getIntrospectionSchemaFieldDefinition();
            } else if (fieldName.equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                return schema.getIntrospectionTypeFieldDefinition();
            }
        }
        return null;
    }

    public void addObjectTypeNames(Collection<String> objectTypeNames) {
        this.objectTypeNames.addAll(objectTypeNames);
    }

    public void setObjectTypeNames(Collection<String> objectTypeNames) {
        this.objectTypeNames.clear();
        this.objectTypeNames.addAll(objectTypeNames);
    }

    public void addChild(ExecutableNormalizedField executableNormalizedField) {
        this.children.add(executableNormalizedField);
    }

    public void clearChildren() {
        this.children.clear();
    }

    /**
     * All merged fields have the same name.
     * <p>
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of of the merged fields.
     */
    public String getName() {
        return getFieldName();
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

    public ImmutableList<Argument> getAstArguments() {
        return astArguments;
    }

    public NormalizedInputValue getNormalizedArgument(String name) {
        return normalizedArguments.get(name);
    }

    public ImmutableMap<String, NormalizedInputValue> getNormalizedArguments() {
        return normalizedArguments;
    }

    public LinkedHashMap<String, Object> getResolvedArguments() {
        return resolvedArguments;
    }


    public static Builder newNormalizedField() {
        return new Builder();
    }


    public String getFieldName() {
        return fieldName;
    }


    public ExecutableNormalizedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }


    /**
     * @return Warning: returns a Mutable Set. No defensive copy is made for performance reasons.
     */
    public Set<String> getObjectTypeNames() {
        return objectTypeNames;
    }

    public String getSingleObjectTypeName() {
        return objectTypeNames.iterator().next();
    }

    public GraphQLObjectType getOneObjectType(GraphQLSchema schema) {
        return (GraphQLObjectType) schema.getType(objectTypeNames.iterator().next());
    }


    public String printDetails() {

        StringBuilder result = new StringBuilder();
        if (getAlias() != null) {
            result.append(getAlias()).append(": ");
        }
        return result + objectTypeNamesToString() + "." + fieldName;
    }

    public String objectTypeNamesToString() {
        if (objectTypeNames.size() == 1) {
            return objectTypeNames.iterator().next();
        } else {
            return objectTypeNames.toString();
        }
    }

    public List<String> getListOfResultKeys() {
        LinkedList<String> list = new LinkedList<>();
        ExecutableNormalizedField current = this;
        while (current != null) {
            list.addFirst(current.getResultKey());
            current = current.parent;
        }
        return list;
    }

    public List<ExecutableNormalizedField> getChildren() {
        return children;
    }

    public List<ExecutableNormalizedField> getChildrenWithSameResultKey(String resultKey) {
        return FpKit.filterList(children, child -> child.getResultKey().equals(resultKey));
    }

    public int getLevel() {
        return level;
    }

    public ExecutableNormalizedField getParent() {
        return parent;
    }

    public void replaceParent(ExecutableNormalizedField newParent) {
        this.parent = newParent;
    }


    @Override
    public String toString() {
        return "NormalizedField{" +
                objectTypeNamesToString() + "." + fieldName +
                ", alias=" + alias +
                ", level=" + level +
                ", children=" + children.stream().map(ExecutableNormalizedField::toString).collect(Collectors.joining("\n")) +
                '}';
    }

    public List<ExecutableNormalizedField> getChildren(int includingRelativeLevel) {
        List<ExecutableNormalizedField> result = new ArrayList<>();
        assertTrue(includingRelativeLevel >= 1, () -> "relative level must be >= 1");

        this.getChildren().forEach(child -> {
            traverseImpl(child, result::add, 1, includingRelativeLevel);
        });
        return result;
    }

    public void traverseSubTree(Consumer<ExecutableNormalizedField> consumer) {
        this.getChildren().forEach(child -> {
            traverseImpl(child, consumer, 1, Integer.MAX_VALUE);
        });
    }

    private void traverseImpl(ExecutableNormalizedField root,
                              Consumer<ExecutableNormalizedField> consumer,
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
        private LinkedHashSet<String> objectTypeNames = new LinkedHashSet<>();
        private String fieldName;
        private ArrayList<ExecutableNormalizedField> children = new ArrayList<>();
        private int level;
        private ExecutableNormalizedField parent;
        private String alias;
        private ImmutableMap<String, NormalizedInputValue> normalizedArguments = ImmutableKit.emptyMap();
        private LinkedHashMap<String, Object> resolvedArguments = new LinkedHashMap<>();
        private ImmutableList<Argument> astArguments = ImmutableKit.emptyList();

        private Builder() {

        }

        private Builder(ExecutableNormalizedField existing) {
            this.alias = existing.alias;
            this.normalizedArguments = existing.normalizedArguments;
            this.astArguments = existing.astArguments;
            this.resolvedArguments = existing.resolvedArguments;
            this.objectTypeNames = new LinkedHashSet<>(existing.getObjectTypeNames());
            this.fieldName = existing.getFieldName();
            this.children = new ArrayList<>(existing.children);
            this.level = existing.getLevel();
            this.parent = existing.getParent();
        }

        public Builder clearObjectTypesNames() {
            this.objectTypeNames.clear();
            return this;
        }

        public Builder objectTypeNames(List<String> objectTypeNames) {
            this.objectTypeNames.addAll(objectTypeNames);
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder normalizedArguments(@Nullable Map<String, NormalizedInputValue> arguments) {
            this.normalizedArguments = arguments == null ? ImmutableKit.emptyMap() : ImmutableMap.copyOf(arguments);
            return this;
        }

        public Builder resolvedArguments(@Nullable Map<String, Object> arguments) {
            this.resolvedArguments = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
            return this;
        }

        public Builder astArguments(@NotNull List<Argument> astArguments) {
            this.astArguments = ImmutableList.copyOf(astArguments);
            return this;
        }


        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }


        public Builder children(List<ExecutableNormalizedField> children) {
            this.children.clear();
            this.children.addAll(children);
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder parent(ExecutableNormalizedField parent) {
            this.parent = parent;
            return this;
        }

        public ExecutableNormalizedField build() {
            return new ExecutableNormalizedField(this);
        }


    }

}
