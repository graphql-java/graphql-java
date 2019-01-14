package graphql.schema;


import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
@Internal
public class DataFetchingEnvironmentImpl implements DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> arguments = new LinkedHashMap<>();
    private final Object context;
    private final Object localContext;
    private final Object root;
    private final GraphQLFieldDefinition fieldDefinition;
    private final MergedField mergedField;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private final ExecutionId executionId;
    private final DataFetchingFieldSelectionSet selectionSet;
    private final ExecutionStepInfo executionStepInfo;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final OperationDefinition operationDefinition;
    private final Document document;
    private final Map<String, Object> variables;

    private DataFetchingEnvironmentImpl(Builder builder) {
        this.source = builder.source;
        this.arguments.putAll(builder.arguments);
        this.context = builder.context;
        this.localContext = builder.localContext;
        this.root = builder.root;
        this.fieldDefinition = builder.fieldDefinition;
        this.mergedField = builder.mergedField;
        this.fieldType = builder.fieldType;
        this.parentType = builder.parentType;
        this.graphQLSchema = builder.graphQLSchema;
        this.fragmentsByName.putAll(builder.fragmentsByName);
        this.executionId = builder.executionId;
        this.selectionSet = builder.selectionSet;
        this.executionStepInfo = builder.executionStepInfo;
        this.dataLoaderRegistry = builder.dataLoaderRegistry;
        this.operationDefinition = builder.operationDefinition;
        this.document = builder.document;
        this.variables = builder.variables;
    }

    @Override
    public <T> T getSource() {
        return (T) source;
    }

    @Override
    public Map<String, Object> getArguments() {
        return new LinkedHashMap<>(arguments);
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
    public <T> T getLocalContext() {
        return (T) localContext;
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
        return mergedField.getFields();
    }

    @Override
    public Field getField() {
        return mergedField.getSingleField();
    }

    @Override
    public MergedField getMergedField() {
        return mergedField;
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
        return new LinkedHashMap<>(fragmentsByName);
    }

    @Override
    public ExecutionId getExecutionId() {
        return executionId;
    }

    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return selectionSet;
    }

    @Override
    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    @Override
    public <K, V> DataLoader<K, V> getDataLoader(String dataLoaderName) {
        return dataLoaderRegistry.getDataLoader(dataLoaderName);
    }

    @Override
    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    @Override
    public Document getDocument() {
        return document;
    }

    @Override
    public Map<String, Object> getVariables() {
        return new LinkedHashMap<>(variables);
    }

    @Override
    public String toString() {
        return "DataFetchingEnvironmentImpl{" +
                "executionStepInfo=" + executionStepInfo +
                '}';
    }

    /**
     * @return a new {@link graphql.schema.DataFetchingEnvironmentImpl.Builder}
     */
    public static Builder newDataFetchingEnvironment() {
        return new Builder();
    }

    public static Builder newDataFetchingEnvironment(DataFetchingEnvironment environment) {
        return new Builder((DataFetchingEnvironmentImpl) environment);
    }

    public static Builder newDataFetchingEnvironment(ExecutionContext executionContext) {
        return new Builder()
                .context(executionContext.getContext())
                .root(executionContext.getRoot())
                .graphQLSchema(executionContext.getGraphQLSchema())
                .fragmentsByName(executionContext.getFragmentsByName())
                .dataLoaderRegistry(executionContext.getDataLoaderRegistry())
                .document(executionContext.getDocument())
                .operationDefinition(executionContext.getOperationDefinition())
                .variables(executionContext.getVariables())
                .executionId(executionContext.getExecutionId());
    }

    public static class Builder {

        private Object source;
        private Object context;
        private Object localContext;
        private Object root;
        private GraphQLFieldDefinition fieldDefinition;
        private MergedField mergedField;
        private GraphQLOutputType fieldType;
        private GraphQLType parentType;
        private GraphQLSchema graphQLSchema;
        private ExecutionId executionId;
        private DataFetchingFieldSelectionSet selectionSet;
        private ExecutionStepInfo executionStepInfo;
        private DataLoaderRegistry dataLoaderRegistry;
        private OperationDefinition operationDefinition;
        private Document document;
        private final Map<String, Object> arguments = new LinkedHashMap<>();
        private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private final Map<String, Object> variables = new LinkedHashMap<>();

        public Builder(DataFetchingEnvironmentImpl env) {
            this.source = env.source;
            this.arguments.putAll(env.arguments);
            this.context = env.context;
            this.localContext = env.localContext;
            this.root = env.root;
            this.fieldDefinition = env.fieldDefinition;
            this.mergedField = env.mergedField;
            this.fieldType = env.fieldType;
            this.parentType = env.parentType;
            this.graphQLSchema = env.graphQLSchema;
            this.fragmentsByName.putAll(env.fragmentsByName);
            this.executionId = env.executionId;
            this.selectionSet = env.selectionSet;
            this.executionStepInfo = env.executionStepInfo;
            this.dataLoaderRegistry = env.dataLoaderRegistry;
            this.operationDefinition = env.operationDefinition;
            this.document = env.document;
            this.variables.putAll(env.variables);
        }

        public Builder() {
        }

        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments.putAll(arguments == null ? Collections.emptyMap() : arguments);
            return this;
        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder mergedField(MergedField mergedField) {
            this.mergedField = mergedField;
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
            this.fragmentsByName.putAll(fragmentsByName == null ? Collections.emptyMap() : fragmentsByName);
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

        public Builder executionStepInfo(ExecutionStepInfo executionStepInfo) {
            this.executionStepInfo = executionStepInfo;
            return this;
        }

        public Builder dataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
            this.dataLoaderRegistry = dataLoaderRegistry;
            return this;
        }

        public Builder operationDefinition(OperationDefinition operationDefinition) {
            this.operationDefinition = operationDefinition;
            return this;
        }

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables.putAll(variables == null ? Collections.emptyMap() : variables);
            return this;
        }

        public DataFetchingEnvironment build() {
            return new DataFetchingEnvironmentImpl(this);
        }
    }
}
