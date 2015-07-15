package graphql.validation;


import graphql.ShouldNotHappenException;
import graphql.execution.TypeFromAST;
import graphql.language.*;
import graphql.schema.*;

import java.util.ArrayList;
import java.util.List;

import static graphql.introspection.Schema.*;

public class TraversalContext implements QueryLanguageVisitor {
    GraphQLSchema schema;
    List<GraphQLOutputType> typeStack = new ArrayList<>();
    List<GraphQLCompositeType> parentTypeStack = new ArrayList<>();
    List<GraphQLInputType> inputTypeStack = new ArrayList<>();
    List<GraphQLFieldDefinition> fieldDefStack = new ArrayList<>();
    GraphQLDirective directive;
    GraphQLArgument argument;


    public TraversalContext(GraphQLSchema graphQLSchema) {
        this.schema = graphQLSchema;
    }

    @Override
    public void enter(Node node) {
        if (node instanceof OperationDefinition) {
            enterImpl((OperationDefinition) node);
        } else if (node instanceof SelectionSet) {
            enterImpl((SelectionSet) node);
        } else if (node instanceof Field) {
            enterImpl((Field) node);
        } else if (node instanceof Directive) {
            enterImpl((Directive) node);
        } else if (node instanceof InlineFragment) {
            enterImpl((InlineFragment) node);
        } else if (node instanceof FragmentDefinition) {
            enterImpl((FragmentDefinition) node);
        } else if (node instanceof VariableDefinition) {
            enterImpl((VariableDefinition) node);
        } else if (node instanceof Argument) {
            enterImpl((Argument) node);
        } else if (node instanceof ArrayValue) {
            enterImpl((ArrayValue) node);
        } else if (node instanceof ObjectField) {
            enterImpl((ObjectField) node);
        } else {
//            throw new ShouldNotHappenException();
        }
    }


    private void enterImpl(SelectionSet selectionSet) {
        GraphQLUnmodifiedType rawType = SchemaUtil.getUnmodifiedType(getType());
        GraphQLCompositeType compositeType = null;
        if (rawType instanceof GraphQLCompositeType) {
            compositeType = (GraphQLCompositeType) rawType;
        }
        addParentType(compositeType);
    }

    private void enterImpl(Field field) {
        GraphQLCompositeType parentType = getParentType();
        GraphQLFieldDefinition fieldDefinition = null;
        if (parentType != null) {
            fieldDefinition = getFieldDef(schema, parentType, field);
        }
        addFieldDef(fieldDefinition);
        addType(fieldDefinition != null ? fieldDefinition.getType() : null);
    }

    private void enterImpl(Directive directive) {
        this.directive = schema.getDirective(directive.getName());
    }

    private void enterImpl(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            addType(schema.getMutationType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            addType(schema.getQueryType());
        } else {
            throw new ShouldNotHappenException();
        }
    }

    private void enterImpl(InlineFragment inlineFragment) {
        GraphQLType type = SchemaUtil.findType(schema, inlineFragment.getTypeCondition().getName());
        addType((GraphQLOutputType) type);
    }

    private void enterImpl(FragmentDefinition fragmentDefinition) {
        GraphQLType type = SchemaUtil.findType(schema, fragmentDefinition.getTypeCondition().getName());
        addType((GraphQLOutputType) type);
    }

    private void enterImpl(VariableDefinition variableDefinition) {
        GraphQLType type = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
        addInputType((GraphQLInputType) type);
    }

    private void enterImpl(Argument argument) {
        if (getDirective() != null) {
            GraphQLArgument fieldArgument = find(getDirective().getArguments(), argument.getName());
            if (fieldArgument != null) {
                this.argument = fieldArgument;
                addInputType(fieldArgument.getType());
            }
        }
        if (getFieldDef() != null) {
            GraphQLArgument fieldArgument = find(getFieldDef().getArguments(), argument.getName());
            if (fieldArgument != null) {
                this.argument = fieldArgument;
                addInputType(fieldArgument.getType());
            }
        }
    }

    private void enterImpl(ArrayValue arrayValue) {
        GraphQLNullableType nullableType = getNullableType(getInputType());
        GraphQLInputType inputType = null;
        if (nullableType instanceof GraphQLList) {
            inputType = (GraphQLInputType) ((GraphQLList) nullableType).getWrappedType();
        }
        addInputType(inputType);
    }

    private void enterImpl(ObjectField objectField) {
        GraphQLUnmodifiedType objectType = SchemaUtil.getUnmodifiedType(getInputType());
        GraphQLInputType inputType = null;
        if (objectType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectField inputField = ((GraphQLInputObjectType) objectType).getField(objectField.getName());
            inputType = inputField.getType();
        }
        addInputType(inputType);
    }

    private GraphQLArgument find(List<GraphQLArgument> arguments, String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }


    @Override
    public void leave(Node node) {
        if (node instanceof OperationDefinition) {
            typeStack.remove(typeStack.size() - 1);
        } else if (node instanceof SelectionSet) {
            parentTypeStack.remove(parentTypeStack.size() - 1);
        } else if (node instanceof Field) {
            fieldDefStack.remove(fieldDefStack.size() - 1);
            typeStack.remove(typeStack.size() - 1);
        } else if (node instanceof Directive) {
            directive = null;
        } else if (node instanceof InlineFragment) {
            typeStack.remove(typeStack.size() - 1);
        } else if (node instanceof FragmentDefinition) {
            typeStack.remove(typeStack.size() - 1);
        } else if (node instanceof VariableDefinition) {
            argument = null;
        } else if (node instanceof Argument) {
            argument = null;
        } else if (node instanceof ArrayValue) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else if (node instanceof ObjectField) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else {
//            throw new ShouldNotHappenException();
        }

    }


    private GraphQLNullableType getNullableType(GraphQLType type) {
        return (GraphQLNullableType) (type instanceof GraphQLNonNull ? ((GraphQLNonNull) type).getWrappedType() : type);
    }

    public GraphQLOutputType getType() {
        return lastElement(typeStack);
    }

    private void addType(GraphQLOutputType type) {
        typeStack.add(type);
    }


    private <T> T lastElement(List<T> list) {
        if (list.size() == 0) return null;
        return list.get(list.size() - 1);
    }

    public GraphQLCompositeType getParentType() {
        return lastElement(parentTypeStack);
    }

    private void addParentType(GraphQLCompositeType compositeType) {
        parentTypeStack.add(compositeType);
    }

    public GraphQLInputType getInputType() {
        return lastElement(inputTypeStack);
    }

    private void addInputType(GraphQLInputType graphQLInputType) {
        inputTypeStack.add(graphQLInputType);
    }

    public GraphQLFieldDefinition getFieldDef() {
        return lastElement(fieldDefStack);
    }

    private void addFieldDef(GraphQLFieldDefinition fieldDefinition) {
        fieldDefStack.add(fieldDefinition);
    }

    public GraphQLDirective getDirective() {
        return directive;
    }

    public GraphQLArgument getArgument() {
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
