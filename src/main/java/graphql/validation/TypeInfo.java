package graphql.validation;


import graphql.ShouldNotHappenException;
import graphql.execution.TypeFromAST;
import graphql.introspection.Schema;
import graphql.language.*;
import graphql.schema.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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
        this.schema = graphQLSchema;
    }

    @Override
    public void enter(Node node) {
        if (node instanceof SelectionSet) {
            enterImpl((SelectionSet) node);
        } else if (node instanceof Field) {
            enterImpl((Field) node);
        } else if (node instanceof Directive) {
            enterImpl((Directive) node);
        } else if (node instanceof OperationDefinition) {
            enterImpl((OperationDefinition) node);
        } else if (node instanceof InlineFragment) {
            enterImpl((InlineFragment) node);
        } else if (node instanceof FragmentDefinition) {
            enterImpl((FragmentDefinition) node);
        } else if (node instanceof VariableDefinition) {
            enterImpl((VariableDefinition) node);
        } else if (node instanceof Argument) {
            enterImpl((Argument) node);
        } else if (node instanceof ListType) {
            enterImpl((ListType) node);
        } else {
            throw new ShouldNotHappenException();
        }
    }


    private void enterImpl(SelectionSet selectionSet) {
        GraphQLUnmodifiedType rawType = getUnmodifiedType(getType());
        GraphQLCompositeType compositeType = null;
        if (rawType instanceof GraphQLCompositeType) {
            compositeType = (GraphQLCompositeType) rawType;
        }
        parentTypeStack.addFirst(compositeType);
    }

    private void enterImpl(Field field) {
        GraphQLCompositeType parentType = getParentType();
        GraphQLFieldDefinition fieldDefinition = null;
        if (parentType != null) {
            fieldDefinition = getFieldDef(schema, parentType, field);
        }
        fieldDefStack.addFirst(fieldDefinition);
        typeStack.addFirst(fieldDefinition != null ? fieldDefinition.getType() : null);
    }

    private void enterImpl(Directive directive) {
        this.directive = schema.getDirective(directive.getName());
    }

    private void enterImpl(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            typeStack.addFirst(schema.getMutationType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            typeStack.addFirst(schema.getQueryType());
        } else {
            throw new ShouldNotHappenException();
        }
    }

    private void enterImpl(InlineFragment inlineFragment) {
        GraphQLType type = SchemaUtil.findType(schema, inlineFragment.getTypeCondition().getName());
        typeStack.addFirst((GraphQLOutputType) type);
    }

    private void enterImpl(FragmentDefinition fragmentDefinition) {
        GraphQLType type = SchemaUtil.findType(schema, fragmentDefinition.getTypeCondition().getName());
        typeStack.addFirst((GraphQLOutputType) type);
    }

    private void enterImpl(VariableDefinition variableDefinition) {
        GraphQLType type = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
        inputTypeStack.addFirst((GraphQLInputType) type);
    }

    private void enterImpl(Argument argument) {
        if (getDirective() != null) {
            GraphQLFieldArgument fieldArgument = find(getDirective().getArguments(), argument.getName());
            if (fieldArgument != null) {
                this.argument = fieldArgument;
                inputTypeStack.addFirst(fieldArgument.getType());
            }
        }
        if (getFieldDef() != null) {
            GraphQLFieldArgument fieldArgument = find(getFieldDef().getArguments(), argument.getName());
            if (fieldArgument != null) {
                this.argument = fieldArgument;
                inputTypeStack.addFirst(fieldArgument.getType());
            }
        }
    }

    private void enterImpl(ListType listType) {
        GraphQLNullableType nullableType = getNullableType(getInputType());
        if (nullableType instanceof GraphQLList) {
            inputTypeStack.addFirst((GraphQLInputType) ((GraphQLList) nullableType).getWrappedType());
        } else {
            inputTypeStack.addFirst(null);
        }
    }

// TODO: OBJECT_FIELD??
//    private void enter(ObjectFie){
//        GraphQLUnmodifiedType objectType = getUnmodifiedType(getInputType());
//        if (objectType instanceof GraphQLInputObjectType) {
//            var inputField = ((GraphQLInputObjectType) objectType).getField()
//            fieldType = inputField ? inputField.type : undefined;
//        }
//        this._inputTypeStack.push(fieldType);
//    }

    private GraphQLFieldArgument find(List<GraphQLFieldArgument> arguments, String name) {
        for (GraphQLFieldArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }


    @Override
    public void leave(Node node) {
        if (node instanceof SelectionSet) {
            parentTypeStack.pop();
        } else if (node instanceof Field) {
            fieldDefStack.pop();
            typeStack.pop();
        } else if (node instanceof Directive) {
            directive = null;
        } else if (node instanceof OperationDefinition) {
            typeStack.pop();
        } else if (node instanceof InlineFragment) {
            typeStack.pop();
        } else if (node instanceof FragmentDefinition) {
            typeStack.pop();
        } else if (node instanceof VariableDefinition) {
            argument = null;
        } else if (node instanceof Argument) {
            argument = null;
        } else if (node instanceof ListType) {
            inputTypeStack.pop();
        }
        // TODO: OBJECT_FIELD??
//        else if(node instanceof  ListType){
//
//        }


    }

    private GraphQLUnmodifiedType getUnmodifiedType(GraphQLType graphQLType) {
        if (graphQLType instanceof GraphQLModifiedType) {
            return getUnmodifiedType(((GraphQLModifiedType) graphQLType).getWrappedType());
        }
        return (GraphQLUnmodifiedType) graphQLType;
    }

    private GraphQLNullableType getNullableType(GraphQLType type) {
        return (GraphQLNullableType) (type instanceof GraphQLNonNull ? ((GraphQLNonNull) type).getWrappedType() : type);
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
