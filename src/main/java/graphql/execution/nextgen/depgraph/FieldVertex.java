package graphql.execution.nextgen.depgraph;

import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;

public class FieldVertex extends Object {
    private final List<Field> fields;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLFieldsContainer fieldsContainer;
    private final GraphQLOutputType parentType;
    private final List<GraphQLObjectType> possibleObjectTypes;

    private List<FieldVertex> children = new ArrayList<>();

    public FieldVertex(List<Field> fields,
                       GraphQLFieldDefinition fieldDefinition,
                       GraphQLFieldsContainer fieldsContainer,
                       GraphQLOutputType parentType,
                       List<GraphQLObjectType> possibleObjectTypes) {
        this.fields = fields;
        this.fieldDefinition = fieldDefinition;
        this.fieldsContainer = fieldsContainer;
        this.parentType = parentType;
        this.possibleObjectTypes = possibleObjectTypes;
    }

    public void addChild(FieldVertex fieldVertex) {
        this.children.add(fieldVertex);
    }

    public void setChildren(List<FieldVertex> children) {
        this.children = new ArrayList<>(children);
    }


    public List<Field> getFields() {
        return fields;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public GraphQLFieldsContainer getFieldsContainer() {
        return fieldsContainer;
    }

    public GraphQLOutputType getParentType() {
        return parentType;
    }

    public List<GraphQLObjectType> getPossibleObjectTypes() {
        return possibleObjectTypes;
    }

    public List<FieldVertex> getChildren() {
        return children;
    }

    public String getName() {
        return fields.get(0).getName();
    }

    public Field getSingleField() {
        return fields.get(0);
    }

    public String getResultKey() {
        Field singleField = getSingleField();
        if (singleField.getAlias() != null) {
            return singleField.getAlias();
        }
        return singleField.getName();
    }

    @Override
    public String toString() {
        if (fields == null) {
            return "ROOT VERTEX";
        }
        return fields.get(0).getName() + FpKit.map(getPossibleObjectTypes(), GraphQLObjectType::getName);
    }
}
