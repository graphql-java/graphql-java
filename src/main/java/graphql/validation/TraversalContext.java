package graphql.validation;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.TypeFromAST;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.ObjectField;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNullableType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

@Internal
@NullMarked
public class TraversalContext implements DocumentVisitor {
    private final GraphQLSchema schema;
    private final List<GraphQLOutputType> outputTypeStack = new ArrayList<>();
    private final List<GraphQLCompositeType> parentTypeStack = new ArrayList<>();
    private final List<GraphQLInputType> inputTypeStack = new ArrayList<>();
    private final List<InputValueWithState> defaultValueStack = new ArrayList<>();
    private final List<GraphQLFieldDefinition> fieldDefStack = new ArrayList<>();
    private final List<String> nameStack = new ArrayList<>();
    private @Nullable GraphQLDirective directive;
    private @Nullable GraphQLArgument argument;


    public TraversalContext(GraphQLSchema graphQLSchema) {
        this.schema = graphQLSchema;
    }

    @Override
    public void enter(Node node, List<Node> path) {
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
        }
    }


    private void enterImpl(SelectionSet selectionSet) {
        GraphQLOutputType outputType = getOutputType();
        GraphQLUnmodifiedType rawType = outputType != null ? unwrapAll(outputType) : null;
        GraphQLCompositeType parentType = null;
        if (rawType instanceof GraphQLCompositeType) {
            parentType = (GraphQLCompositeType) rawType;
        }
        addParentType(parentType);
    }

    private void enterImpl(Field field) {
        enterName(field.getName());
        GraphQLCompositeType parentType = getParentType();
        GraphQLFieldDefinition fieldDefinition = null;
        if (parentType != null) {
            fieldDefinition = getFieldDef(schema, parentType, field);
        }
        addFieldDef(fieldDefinition);
        addOutputType(fieldDefinition != null ? fieldDefinition.getType() : null);
    }

    private void enterImpl(Directive directive) {
        this.directive = schema.getDirective(directive.getName());
    }

    private void enterImpl(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            addOutputType(schema.getMutationType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            addOutputType(schema.getQueryType());
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            addOutputType(schema.getSubscriptionType());
        } else {
            Assert.assertShouldNeverHappen();
        }
    }

    private void enterImpl(InlineFragment inlineFragment) {
        TypeName typeCondition = inlineFragment.getTypeCondition();
        GraphQLOutputType type;
        if (typeCondition != null) {
            GraphQLType typeConditionType = schema.getType(typeCondition.getName());
            if (typeConditionType instanceof GraphQLOutputType) {
                type = (GraphQLOutputType) typeConditionType;
            } else {
                type = null;
            }
        } else {
            type = getParentType();
        }
        addOutputType(type);
    }

    private void enterImpl(FragmentDefinition fragmentDefinition) {
        enterName(fragmentDefinition.getName());
        GraphQLType type = schema.getType(fragmentDefinition.getTypeCondition().getName());
        addOutputType(type instanceof GraphQLOutputType ? (GraphQLOutputType) type : null);
    }

    private void enterImpl(VariableDefinition variableDefinition) {
        GraphQLType type = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
        addInputType(type instanceof GraphQLInputType ? (GraphQLInputType) type : null);
    }

    private void enterImpl(Argument argument) {
        GraphQLArgument argumentType = null;
        if (getDirective() != null) {
            argumentType = find(getDirective().getArguments(), argument.getName());
        } else if (getFieldDef() != null) {
            argumentType = find(getFieldDef().getArguments(), argument.getName());
        }

        addInputType(argumentType != null ? argumentType.getType() : null);
        addDefaultValue(argumentType != null ? argumentType.getArgumentDefaultValue() : null);
        this.argument = argumentType;
    }

    private void enterImpl(ArrayValue arrayValue) {
        GraphQLNullableType nullableType = getNullableType(getInputType());
        GraphQLInputType inputType = null;
        if (nullableType != null && isList(nullableType)) {
            inputType = (GraphQLInputType) unwrapOne(nullableType);
        }
        addInputType(inputType);
        // List positions never have a default value. See graphql-js impl for inspiration
        addDefaultValue(null);
    }

    private void enterImpl(ObjectField objectField) {
        GraphQLInputType currentInputType = getInputType();
        GraphQLUnmodifiedType objectType = currentInputType != null ? unwrapAll(currentInputType) : null;
        GraphQLInputType inputType = null;
        GraphQLInputObjectField inputField = null;
        if (objectType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) objectType;
            inputField = schema.getCodeRegistry().getFieldVisibility().getFieldDefinition(inputObjectType, objectField.getName());
            if (inputField != null) {
                inputType = inputField.getType();
            }
        }
        addInputType(inputType);
        addDefaultValue(inputField != null ? inputField.getInputFieldDefaultValue() : null);
    }

    private @Nullable GraphQLArgument find(List<GraphQLArgument> arguments, String name) {
        for (GraphQLArgument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }


    @Override
    public void leave(Node node, List<Node> ancestors) {
        if (node instanceof OperationDefinition) {
            pop(outputTypeStack);
        } else if (node instanceof SelectionSet) {
            pop(parentTypeStack);
        } else if (node instanceof Field) {
            leaveName(((Field) node).getName());
            pop(fieldDefStack);
            pop(outputTypeStack);
        } else if (node instanceof Directive) {
            directive = null;
        } else if (node instanceof InlineFragment) {
            pop(outputTypeStack);
        } else if (node instanceof FragmentDefinition) {
            leaveName(((FragmentDefinition) node).getName());
            pop(outputTypeStack);
        } else if (node instanceof VariableDefinition) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else if (node instanceof Argument) {
            argument = null;
            pop(inputTypeStack);
            pop(defaultValueStack);
        } else if (node instanceof ArrayValue) {
            pop(inputTypeStack);
            pop(defaultValueStack);
        } else if (node instanceof ObjectField) {
            pop(inputTypeStack);
            pop(defaultValueStack);
        }
    }

    private void enterName(String name) {
        if (!isEmpty(name)) {
            nameStack.add(name);
        }
    }

    private void leaveName(String name) {
        if (!isEmpty(name)) {
            nameStack.remove(nameStack.size() - 1);
        }
    }

    private boolean isEmpty(String name) {
        return name == null || name.isEmpty();
    }

    private @Nullable GraphQLNullableType getNullableType(@Nullable GraphQLType type) {
        if (type == null) {
            return null;
        }
        return (GraphQLNullableType) (isNonNull(type) ? unwrapOne(type) : type);
    }

    /**
     * @return can be null if current node does not have a OutputType associated: for example
     * if the current field is unknown
     */
    public @Nullable GraphQLOutputType getOutputType() {
        return lastElement(outputTypeStack);
    }

    private void addOutputType(@Nullable GraphQLOutputType type) {
        outputTypeStack.add(type);
    }

    private <T> @Nullable T lastElement(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    private <T>  T pop(List<T> list) {
        return list.remove(list.size() - 1);
    }

    /**
     * @return can be null if the parent is not a CompositeType
     */
    public @Nullable GraphQLCompositeType getParentType() {
        return lastElement(parentTypeStack);
    }

    private void addParentType(@Nullable GraphQLCompositeType compositeType) {
        parentTypeStack.add(compositeType);
    }

    public @Nullable GraphQLInputType getInputType() {
        return lastElement(inputTypeStack);
    }

    public @Nullable InputValueWithState getDefaultValue() {
        return lastElement(defaultValueStack);
    }

    private void addInputType(@Nullable GraphQLInputType graphQLInputType) {
        inputTypeStack.add(graphQLInputType);
    }

    private void addDefaultValue(@Nullable InputValueWithState defaultValue) {
        defaultValueStack.add(defaultValue);
    }

    public @Nullable GraphQLFieldDefinition getFieldDef() {
        return lastElement(fieldDefStack);
    }

    public @Nullable List<String> getQueryPath() {
        if (nameStack.isEmpty()) {
            return null;
        }
        return new ArrayList<>(nameStack);
    }

    private void addFieldDef(@Nullable GraphQLFieldDefinition fieldDefinition) {
        fieldDefStack.add(fieldDefinition);
    }

    public @Nullable GraphQLDirective getDirective() {
        return directive;
    }

    public @Nullable GraphQLArgument getArgument() {
        return argument;
    }

    private @Nullable GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLType parentType, Field field) {
        if (schema.getQueryType().equals(parentType)) {
            if (field.getName().equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                return schema.getIntrospectionSchemaFieldDefinition();
            }
            if (field.getName().equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                return schema.getIntrospectionTypeFieldDefinition();
            }
        }
        if (field.getName().equals(schema.getIntrospectionTypenameFieldDefinition().getName())
                && (parentType instanceof GraphQLObjectType ||
                parentType instanceof GraphQLInterfaceType ||
                parentType instanceof GraphQLUnionType)) {
            return schema.getIntrospectionTypenameFieldDefinition();
        }
        if (parentType instanceof GraphQLFieldsContainer) {
            return schema.getCodeRegistry().getFieldVisibility().getFieldDefinition((GraphQLFieldsContainer) parentType, field.getName());
        }
        return null;
    }
}
