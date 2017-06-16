package graphql.schema;


import graphql.execution.ExecutionId;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class DataFetchingEnvironmentImpl implements DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> variables;
    private final Object context;
    private final Object root;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ExecutionId executionId;
    private final DataFetchingFieldSelectionSet selectionSet;

    public DataFetchingEnvironmentImpl(Object source, Map<String, Object> variables, Object context, Object root, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema, Map<String, FragmentDefinition> fragmentsByName, ExecutionId executionId, DataFetchingFieldSelectionSet selectionSet) {
        this.source = source;
        this.variables = variables;
        this.context = context;
        this.root = root;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
        this.fragmentsByName = fragmentsByName;
        this.executionId = executionId;
        this.selectionSet = selectionSet;
    }

    @Override
    public <T> T getSource() {
        return (T) source;
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public boolean containsVariable(String name) {
        return variables.containsKey(name);
    }

    @Override
    public <T> T getVariable(String name) {
        return (T) variables.get(name);
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
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return selectionSet;
    }
}
