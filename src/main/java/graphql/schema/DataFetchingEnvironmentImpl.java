package graphql.schema;


import graphql.Internal;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionPath;
import graphql.execution.TypeInfo;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
@Internal
public class DataFetchingEnvironmentImpl implements DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> arguments;
    private final Object context;
    private final Object root;
    private final GraphQLFieldDefinition fieldDefinition;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ExecutionId executionId;
    private final TypeInfo typeInfo;
    private final ExecutionPath executionPath;
    private final DataFetchingFieldSelectionSet selectionSet;

    public DataFetchingEnvironmentImpl(Object source, Map<String, Object> arguments, Object context, Object root, GraphQLFieldDefinition fieldDefinition, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema, Map<String, FragmentDefinition> fragmentsByName, ExecutionId executionId, TypeInfo typeInfo, ExecutionPath executionPath, DataFetchingFieldSelectionSet selectionSet) {
        this.source = source;
        this.arguments = arguments;
        this.context = context;
        this.root = root;
        this.fieldDefinition = fieldDefinition;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
        this.fragmentsByName = fragmentsByName;
        this.executionId = executionId;
        this.typeInfo = typeInfo;
        this.executionPath = executionPath;
        this.selectionSet = selectionSet;
    }

    @Override
    public <T> T getSource() {
        return (T) source;
    }

    @Override
    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public boolean containsArgument(String name) {
        return arguments.containsKey(name);
    }

    @Override
    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    @Override
    public <T> T getContext() {
        return (T) context;
    }

    @Override
    public <T> T getRoot() {
        return (T) root;
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }

    @Override
    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    @Override
    public GraphQLType getParentType() {
        return parentType;
    }

    @Override
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    @Override
    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    @Override
    public ExecutionId getExecutionId() {
        return executionId;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public ExecutionPath getExecutionPath() {
        return executionPath;
    }

    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return selectionSet;
    }
}
