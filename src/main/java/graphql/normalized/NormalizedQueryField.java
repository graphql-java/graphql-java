package graphql.normalized;

import graphql.Internal;
import graphql.execution.MergedField;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

@Internal
public class NormalizedQueryField {

    private final MergedField mergedField;
    private final GraphQLObjectType objectType;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLOutputType parentType;
    // this is the unwrapped parent type: can be object or interface
    private final GraphQLFieldsContainer fieldsContainer;
    private final List<NormalizedQueryField> children;
    private final boolean isConditional;

    private NormalizedQueryField(Builder builder) {
        this.mergedField = builder.mergedField;
        this.objectType = builder.objectType;
        this.fieldDefinition = assertNotNull(builder.fieldDefinition);
        this.fieldsContainer = assertNotNull(builder.fieldsContainer);
        this.parentType = assertNotNull(builder.parentType);
        this.children = builder.children;
        this.isConditional = objectType != fieldsContainer;
    }

    /**
     * All merged fields have the same name.
     *
     * WARNING: This is not always the key in the execution result, because of possible aliases. See {@link #getResultKey()}
     *
     * @return the name of of the merged fields.
     */
    public String getName() {
        return mergedField.getName();
    }

    /**
     * Returns the key of this MergedFieldWithType for the overall result.
     * This is either an alias or the FieldWTC name.
     *
     * @return the key for this MergedFieldWithType.
     */
    public String getResultKey() {
        Field singleField = getSingleField();
        if (singleField.getAlias() != null) {
            return singleField.getAlias();
        }
        return singleField.getName();
    }

    /**
     * The first of the merged fields.
     *
     * Because all fields are almost identically
     * often only one of the merged fields are used.
     *
     * @return the fist of the merged Fields
     */
    public Field getSingleField() {
        return mergedField.getSingleField();
    }

    /**
     * All merged fields share the same arguments.
     *
     * @return the list of arguments
     */
    public List<Argument> getArguments() {
        return getSingleField().getArguments();
    }


    public MergedField getMergedField() {
        return mergedField;
    }

    public static Builder newQueryExecutionField() {
        return new Builder();
    }

    public static Builder newQueryExecutionField(Field field) {
        return new Builder().mergedField(MergedField.newMergedField().addField(field).build());
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }


    public NormalizedQueryField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public GraphQLObjectType getObjectType() {
        return objectType;
    }

    public GraphQLFieldsContainer getFieldsContainer() {
        return fieldsContainer;
    }

    public GraphQLOutputType getParentType() {
        return parentType;
    }

    public String print() {
        StringBuilder result = new StringBuilder();
        Field singleField = getSingleField();
        if (singleField.getAlias() != null) {
            result.append(singleField.getAlias()).append(": ");
        }
        return result + objectType.getName() + "." + fieldDefinition.getName() + ": " + simplePrint(fieldDefinition.getType()) +
                " (conditional: " + this.isConditional + ")";
    }

    public List<NormalizedQueryField> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "QueryExecutionField{" +
                "mergedField" + mergedField +
                ", objectType=" + objectType +
                ", fieldDefinition=" + fieldDefinition +
                ", parentType=" + parentType +
                ", fieldsContainer=" + fieldsContainer +
                ", children=" + children +
                ", isConditional=" + isConditional +
                '}';
    }

    public static class Builder {
        private MergedField mergedField;
        private GraphQLObjectType objectType;
        private GraphQLFieldDefinition fieldDefinition;
        private GraphQLFieldsContainer fieldsContainer;
        private GraphQLOutputType parentType;
        private List<NormalizedQueryField> children = new ArrayList<>();

        private Builder() {

        }

        private Builder(NormalizedQueryField existing) {
            this.mergedField = existing.getMergedField();
            this.objectType = existing.getObjectType();
            this.fieldDefinition = existing.getFieldDefinition();
            this.fieldsContainer = existing.getFieldsContainer();
            this.parentType = existing.getParentType();
        }


        public Builder objectType(GraphQLObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder mergedField(MergedField mergedField) {
            this.mergedField = mergedField;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder fieldsContainer(GraphQLFieldsContainer fieldsContainer) {
            this.fieldsContainer = fieldsContainer;
            return this;
        }

        public Builder parentType(GraphQLOutputType parentType) {
            this.parentType = parentType;
            return this;
        }

        public Builder children(List<NormalizedQueryField> children) {
            this.children.clear();
            this.children.addAll(children);
            return this;
        }

        public NormalizedQueryField build() {
            return new NormalizedQueryField(this);
        }


    }

}
