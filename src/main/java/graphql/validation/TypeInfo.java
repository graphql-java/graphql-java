package graphql.validation;


import graphql.ShouldNotHappenException;
import graphql.introspection.Schema;
import graphql.language.*;
import graphql.schema.*;

import java.util.ArrayDeque;
import java.util.Deque;

import static graphql.introspection.Schema.*;

public class TypeInfo implements QueryLanguageVisitor {
    GraphQLSchema schema;
    Deque<GraphQLOutputType> typeStack = new ArrayDeque<>();
    Deque<GraphQLCompositeType> parentTypeStack = new ArrayDeque<>();
    Deque<GraphQLInputType> inputTypeStack = new ArrayDeque<>();
    Deque<GraphQLFieldDefinition> fieldDefStack = new ArrayDeque<>();
    GraphQLDirective directive;
    GraphQLFieldArgument argument;


    public TypeInfo(GraphQLSchema graphQLSchema) {
        this.schema = schema;
    }

    @Override
    public void enter(Node node) {
//        enterImpl(node);
    }


    private GraphQLUnmodifiedType getUnmodifiedType(GraphQLType graphQLType) {
        if (graphQLType instanceof GraphQLModifiedType) {
            return getUnmodifiedType(((GraphQLModifiedType) graphQLType).getWrappedType());
        }
        return (GraphQLUnmodifiedType) graphQLType;
    }

    public GraphQLOutputType getType() {
        return typeStack.getFirst();
    }

    public GraphQLCompositeType getParentType() {
        return parentTypeStack.getFirst();
    }

    public GraphQLInputType getInputType() {
        return inputTypeStack.getFirst();
    }

    public GraphQLFieldDefinition getFieldDef() {
        return fieldDefStack.getFirst();
    }

    public GraphQLDirective getDirective() {
        return directive;
    }

    public GraphQLFieldArgument getArgument() {
        return argument;
    }


    private void enterSelectionSet(SelectionSet selectionSet) {
        GraphQLUnmodifiedType rawType = getUnmodifiedType(getType());
        GraphQLCompositeType compositeType = null;
        if (rawType instanceof GraphQLCompositeType) {
            compositeType = (GraphQLCompositeType) rawType;
        }
        parentTypeStack.addFirst(compositeType);
    }

    private void enterField(Field field) {
        GraphQLCompositeType parentType = getParentType();
        GraphQLFieldDefinition fieldDefinition = null;
        if (parentType != null) {
            fieldDefinition = getFieldDef(schema, parentType, field);
        }
        fieldDefStack.addFirst(fieldDefinition);
        typeStack.addFirst(fieldDefinition != null ? fieldDefinition.getType() : null);
    }

    private void enterDirective(Directive directive) {
        this.directive = schema.getDirective(directive.getName());
    }

    private void enterOperationDefinition(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            typeStack.addFirst(schema.getMutationType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            typeStack.addFirst(schema.getQueryType());
        } else {
            throw new ShouldNotHappenException();
        }
    }

    @Override
    public void leave(Node node) {

    }


    private GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLType parentType, Field field) {
        if (schema.getQueryType().equals(parentType)) {
            if (field.getName().equals(SchemaMetaFieldDef.getName())) {
                return SchemaMetaFieldDef;
            }
            if (field.getName().equals(TypeMetaFieldDef.getName())) {
                return TypeMetaFieldDef;
            }
        }
        if (field.getName().equals(TypeNameMetaFieldDef.getName())
                && (parentType instanceof GraphQLObjectType ||
                parentType instanceof GraphQLInterfaceType ||
                parentType instanceof GraphQLUnionType)) {
            return TypeNameMetaFieldDef;
        }
        if (parentType instanceof GraphQLFieldsContainer) {
            return ((GraphQLFieldsContainer) parentType).getFieldDefinition(field.getName());
        }
        return null;
    }
}
