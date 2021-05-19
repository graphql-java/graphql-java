package graphql.normalized;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.language.Argument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 * Intentionally Mutable: parent and object types can be mutated.
 */
@Internal
public class NormalizedField {
    private final String alias;
    private final Map<String, NormalizedInputValue> normalizedArguments;
    private final Map<String, Object> resolvedArguments;
    private final ImmutableList<Argument> astArguments;
    private final Set<String> objectTypeNames;
    private final String fieldName;
    private final List<NormalizedField> children;
    private final int level;
    private NormalizedField parent;


    private NormalizedField(Builder builder) {
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

    public List<Argument> getAstArguments() {
        return astArguments;
    }

    public NormalizedInputValue getNormalizedArgument(String name) {
        return normalizedArguments.get(name);
    }

    public Map<String, NormalizedInputValue> getNormalizedArguments() {
        return normalizedArguments;
    }

    public Map<String, Object> getResolvedArguments() {
        return resolvedArguments;
    }


    public static Builder newQueryExecutionField() {
        return new Builder();
    }


    public String getFieldName() {
        return fieldName;
    }


    public NormalizedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public Set<String> getObjectTypeNames() {
        return objectTypeNames;
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
        NormalizedField current = this;
        while (current != null) {
            list.addFirst(current.getResultKey());
            current = current.parent;
        }
        return list;
    }

    public List<NormalizedField> getChildren() {
        return children;
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


    @Override
    public String toString() {
        return "NormalizedField{" +
                objectTypeNamesToString() + "." + fieldName +
                ", alias=" + alias +
                ", level=" + level +
                ", children=" + children.stream().map(NormalizedField::toString).collect(Collectors.joining("\n")) +
                '}';
    }

    public List<NormalizedField> getChildren(int includingRelativeLevel) {
        List<NormalizedField> result = new ArrayList<>();
        assertTrue(includingRelativeLevel >= 1, () -> "relative level must be >= 1");

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
        private Set<String> objectTypeNames = new LinkedHashSet<>();
        private String fieldName;
        private List<NormalizedField> children = new ArrayList<>();
        private int level;
        private NormalizedField parent;
        private String alias;
        private Map<String, NormalizedInputValue> normalizedArguments = Collections.emptyMap();
        private Map<String, Object> resolvedArguments = Collections.emptyMap();
        private ImmutableList<Argument> astArguments = ImmutableKit.emptyList();

        private Builder() {

        }

        private Builder(NormalizedField existing) {
            this.alias = existing.alias;
            this.normalizedArguments = existing.normalizedArguments;
            this.astArguments = existing.astArguments;
            this.resolvedArguments = existing.resolvedArguments;
            this.objectTypeNames = existing.getObjectTypeNames();
            this.fieldName = existing.getFieldName();
            this.children = existing.getChildren();
            this.level = existing.getLevel();
            this.parent = existing.getParent();
        }

        public Builder objectTypeNames(List<String> objectTypeNames) {
            this.objectTypeNames.addAll(objectTypeNames);
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder normalizedArguments(Map<String, NormalizedInputValue> arguments) {
            this.normalizedArguments = arguments == null ? Collections.emptyMap() : arguments;
            return this;
        }

        public Builder resolvedArguments(Map<String, Object> arguments) {
            this.resolvedArguments = arguments == null ? Collections.emptyMap() : arguments;
            return this;
        }

        public Builder astArguments(List<Argument> astArguments) {
            this.astArguments = ImmutableList.copyOf(astArguments);
            return this;
        }


        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }


        public Builder children(List<NormalizedField> children) {
            this.children.clear();
            this.children.addAll(children);
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
