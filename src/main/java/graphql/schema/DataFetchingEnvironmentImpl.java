package graphql.schema;


import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class DataFetchingEnvironmentImpl implements DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> arguments;
    private final Object context;
    private final Object root;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ExecutionId executionId;
    private final DataFetchingFieldSelectionSet selectionSet;

    public DataFetchingEnvironmentImpl(Object source, Map<String, Object> arguments, Object context, Object root, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema, Map<String, FragmentDefinition> fragmentsByName, ExecutionId executionId, DataFetchingFieldSelectionSet selectionSet) {
        this.source = source;
        this.arguments = arguments;
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

    public static Builder newDataFetchingEnvironment() {
        return new Builder();
    }

    public static Builder newDataFetchingEnvironment(ExecutionContext executionContext) {
        return new Builder()
                .context(executionContext.getContext())
                .root(executionContext.getRoot())
                .graphQLSchema(executionContext.getGraphQLSchema())
                .fragmentsByName(executionContext.getFragmentsByName())
                .executionId(executionContext.getExecutionId());
    }


    public static Builder newDataFetchingEnvironment(DataFetchingEnvironment environment) {
        return new Builder()
                .source(environment.getSource())
                .arguments(environment.getArguments())
                .context(environment.getContext())
                .root(environment.getRoot())
                .fields(environment.getFields())
                .fieldType(environment.getFieldType())
                .parentType(environment.getParentType())
                .graphQLSchema(environment.getGraphQLSchema())
                .fragmentsByName(environment.getFragmentsByName())
                .executionId(environment.getExecutionId())
                .selectionSet(environment.getSelectionSet());
    }

    public static class Builder {

        private Object source;
        private Map<String, Object> arguments = Collections.emptyMap();
        private Object context;
        private Object root;
        private List<Field> fields = Collections.emptyList();
        private GraphQLOutputType fieldType;
        private GraphQLType parentType;
        private GraphQLSchema graphQLSchema;
        private Map<String, FragmentDefinition> fragmentsByName = Collections.emptyMap();
        private ExecutionId executionId;
        private DataFetchingFieldSelectionSet selectionSet;


        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder fieldType(GraphQLOutputType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder parentType(GraphQLType parentType) {
            this.parentType = parentType;
            return this;
        }

        public Builder graphQLSchema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public Builder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = fragmentsByName;
            return this;
        }

        public Builder executionId(ExecutionId executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder selectionSet(DataFetchingFieldSelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public DataFetchingEnvironment build() {
            return new DataFetchingEnvironmentImpl(source, arguments, context, root,
                    fields, fieldType, parentType, graphQLSchema, fragmentsByName, executionId, selectionSet
            );
        }
    }
}
