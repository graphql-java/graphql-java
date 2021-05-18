package graphql.normalized;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.language.Argument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

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

@Internal
public class NormalizedField {
    private final String alias;
    private final Map<String, NormalizedInputValue> normalizedArguments;
    private final Map<String, Object> arguments;
    private final ImmutableList<Argument> astArguments;
    private final Set<String> objectTypes;
    private final String fieldName;
    private final List<NormalizedField> children;
    //    private final boolean isConditional;
    private final int level;
    private NormalizedField parent;


    private NormalizedField(Builder builder) {
        this.alias = builder.alias;
        this.arguments = builder.arguments;
        this.normalizedArguments = builder.normalizedArguments;
        this.astArguments = builder.astArguments;
        this.objectTypes = builder.objectTypes;
        this.fieldName = assertNotNull(builder.fieldName);
        this.children = builder.children;
        this.level = builder.level;
        this.parent = builder.parent;
        // can be null for the top level fields
//        if (parent == null) {
//            this.isConditional = false;
//        } else {
//            GraphQLUnmodifiedType parentType = GraphQLTypeUtil.unwrapAll(parent.getFieldDefinition().getType());
//            this.isConditional = parentType != this.objectType;
//        }
    }

    public GraphQLType getType(GraphQLSchema schema) {
        return getFieldDefinition(schema).getType();
    }

    public GraphQLFieldDefinition getFieldDefinition(GraphQLSchema schema) {
        GraphQLFieldDefinition fieldDefinition;
        if (fieldName.equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
            fieldDefinition = schema.getIntrospectionTypenameFieldDefinition();
        } else {
            if (fieldName.equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                fieldDefinition = schema.getIntrospectionSchemaFieldDefinition();
            } else if (fieldName.equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                fieldDefinition = schema.getIntrospectionTypeFieldDefinition();
            } else {
                GraphQLObjectType type = (GraphQLObjectType) assertNotNull(schema.getType(objectTypes.iterator().next()));
                fieldDefinition = assertNotNull(type.getField(fieldName), () -> String.format("no field %s found for type %s", fieldName, objectTypes.iterator().next()));
            }
        }
        return fieldDefinition;
    }

    public void addObjectTypes(Collection<String> objectTypes) {
        this.objectTypes.addAll(objectTypes);
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

//    public boolean isConditional() {
//        return isConditional;
//    }


    public List<Argument> getAstArguments() {
        return astArguments;
    }

    public NormalizedInputValue getNormalizedArgument(String name) {
        return normalizedArguments.get(name);
    }

    public Map<String, NormalizedInputValue> getNormalizedArguments() {
        return normalizedArguments;
    }

    public Map<String, Object> getArguments() {
        return arguments;
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

    public Set<String> getObjectTypes() {
        return objectTypes;
    }

    public String printDetails() {

        StringBuilder result = new StringBuilder();
        if (getAlias() != null) {
            result.append(getAlias()).append(": ");
        }
        return result + objectTypes.toString() + "." + fieldName;
    }

    public String print() {
        StringBuilder result = new StringBuilder();
        result.append("(");
        if (getAlias() != null) {
            result.append(getAlias()).append(":");
        }
        return result + objectTypes.toString() + "." + fieldName + ")";
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
                objectTypes.toString() + "." + fieldName +
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
        private Set<String> objectTypes = new LinkedHashSet<>();
        private String fieldName;
        private List<NormalizedField> children = new ArrayList<>();
        private int level;
        private NormalizedField parent;
        private String alias;
        private Map<String, NormalizedInputValue> normalizedArguments = Collections.emptyMap();
        private Map<String, Object> arguments = Collections.emptyMap();
        private ImmutableList<Argument> astArguments = ImmutableKit.emptyList();

        private Builder() {

        }

        private Builder(NormalizedField existing) {
            this.alias = existing.alias;
            this.normalizedArguments = existing.normalizedArguments;
            this.astArguments = existing.astArguments;
            this.arguments = existing.arguments;
            this.objectTypes = existing.getObjectTypes();
            this.fieldName = existing.getFieldName();
            this.children = existing.getChildren();
            this.level = existing.getLevel();
            this.parent = existing.getParent();
        }

        public Builder objectTypes(List<String> objectTypes) {
            this.objectTypes.addAll(objectTypes);
            return this;
        }

//        public Builder objectType(String objectType) {
//            this.objectTypes = ImmutableList.of(objectType);
//            return this;
//        }
//

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder normalizedArguments(Map<String, NormalizedInputValue> arguments) {
            this.normalizedArguments = arguments == null ? Collections.emptyMap() : arguments;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments == null ? Collections.emptyMap() : arguments;
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
