package graphql.schema;


import graphql.Internal;
import graphql.cachecontrol.CacheControl;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.normalized.NormalizedField;
import graphql.normalized.NormalizedQueryFromAst;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
@Internal
public class DataFetchingEnvironmentImpl implements DataFetchingEnvironment {
    private final Object source;
    private final Supplier<Map<String, Object>> arguments;
    private final Object context;
    private final Object localContext;
    private final Object root;
    private final GraphQLFieldDefinition fieldDefinition;
    private final MergedField mergedField;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ExecutionId executionId;
    private final DataFetchingFieldSelectionSet selectionSet;
    private final Supplier<ExecutionStepInfo> executionStepInfo;
    private final DataLoaderRegistry dataLoaderRegistry;
    private final CacheControl cacheControl;
    private final Locale locale;
    private final OperationDefinition operationDefinition;
    private final Document document;
    private final Map<String, Object> variables;
    private final QueryDirectives queryDirectives;
    private final Supplier<NormalizedQueryFromAst> normalizedQueryFromAstSupplier;

    private DataFetchingEnvironmentImpl(Builder builder) {
        this.source = builder.source;
        this.arguments = builder.arguments == null ? Collections::emptyMap : builder.arguments;
        this.context = builder.context;
        this.localContext = builder.localContext;
        this.root = builder.root;
        this.fieldDefinition = builder.fieldDefinition;
        this.mergedField = builder.mergedField;
        this.fieldType = builder.fieldType;
        this.parentType = builder.parentType;
        this.graphQLSchema = builder.graphQLSchema;
        this.fragmentsByName = builder.fragmentsByName == null ? Collections.emptyMap() : builder.fragmentsByName;
        this.executionId = builder.executionId;
        this.selectionSet = builder.selectionSet;
        this.executionStepInfo = builder.executionStepInfo;
        this.dataLoaderRegistry = builder.dataLoaderRegistry;
        this.cacheControl = builder.cacheControl;
        this.locale = builder.locale;
        this.operationDefinition = builder.operationDefinition;
        this.document = builder.document;
        this.variables = builder.variables == null ? Collections.emptyMap() : builder.variables;
        this.queryDirectives = builder.queryDirectives;
        this.normalizedQueryFromAstSupplier = builder.normalizedQueryFromAstSupplier;
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
                .cacheControl(executionContext.getCacheControl())
                .locale(executionContext.getLocale())
                .document(executionContext.getDocument())
                .operationDefinition(executionContext.getOperationDefinition())
                .variables(executionContext.getVariables())
                .executionId(executionContext.getExecutionId())
                .normalizedQuery(executionContext.getNormalizedQuery());
    }

    @Override
    public <T> T getSource() {
        return (T) source;
    }

    @Override
    public Map<String, Object> getArguments() {
        return Collections.unmodifiableMap(arguments.get());
    }

    @Override
    public boolean containsArgument(String name) {
        return arguments.get().containsKey(name);
    }

    @Override
    public <T> T getArgument(String name) {
        return (T) arguments.get().get(name);
    }

    @Override
    public <T> T getArgumentOrDefault(String name, T defaultValue) {
        return (T) arguments.get().getOrDefault(name, defaultValue);
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
        return Collections.unmodifiableMap(fragmentsByName);
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
    public QueryDirectives getQueryDirectives() {
        return queryDirectives;
    }

    @Override
    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

    @Override
    public <K, V> DataLoader<K, V> getDataLoader(String dataLoaderName) {
        return dataLoaderRegistry.getDataLoader(dataLoaderName);
    }

    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry;
    }

    @Override
    public CacheControl getCacheControl() {
        return cacheControl;
    }

    @Override
    public Locale getLocale() {
        return locale;
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
        return Collections.unmodifiableMap(variables);
    }

    @Override
    public NormalizedField getNormalizeField() {
        return normalizedQueryFromAstSupplier.get().getNormalizedField(mergedField, executionStepInfo.get().getPath());
    }

    @Override
    public NormalizedQueryFromAst getNormalizedQueryFromAst() {
        return normalizedQueryFromAstSupplier.get();
    }

    @Override
    public String toString() {
        return "DataFetchingEnvironmentImpl{" +
                "executionStepInfo=" + executionStepInfo +
                '}';
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
        private Supplier<ExecutionStepInfo> executionStepInfo;
        private DataLoaderRegistry dataLoaderRegistry;
        private CacheControl cacheControl;
        private Locale locale;
        private OperationDefinition operationDefinition;
        private Document document;
        private Supplier<Map<String, Object>> arguments;
        private Map<String, FragmentDefinition> fragmentsByName;
        private Map<String, Object> variables;
        private QueryDirectives queryDirectives;
        private Supplier<NormalizedQueryFromAst> normalizedQueryFromAstSupplier;

        public Builder(DataFetchingEnvironmentImpl env) {
            this.source = env.source;
            this.arguments = env.arguments;
            this.context = env.context;
            this.localContext = env.localContext;
            this.root = env.root;
            this.fieldDefinition = env.fieldDefinition;
            this.mergedField = env.mergedField;
            this.fieldType = env.fieldType;
            this.parentType = env.parentType;
            this.graphQLSchema = env.graphQLSchema;
            this.fragmentsByName = env.fragmentsByName;
            this.executionId = env.executionId;
            this.selectionSet = env.selectionSet;
            this.executionStepInfo = env.executionStepInfo;
            this.dataLoaderRegistry = env.dataLoaderRegistry;
            this.cacheControl = env.cacheControl;
            this.locale = env.locale;
            this.operationDefinition = env.operationDefinition;
            this.document = env.document;
            this.variables = env.variables;
            this.queryDirectives = env.queryDirectives;
            this.normalizedQueryFromAstSupplier = env.normalizedQueryFromAstSupplier;
        }

        public Builder() {
        }

        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            return arguments(() -> arguments);
        }

        public Builder arguments(Supplier<Map<String, Object>> arguments) {
            this.arguments = arguments;
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

        public Builder executionStepInfo(ExecutionStepInfo executionStepInfo) {
            return executionStepInfo(() -> executionStepInfo);
        }

        public Builder executionStepInfo(Supplier<ExecutionStepInfo> executionStepInfo) {
            this.executionStepInfo = executionStepInfo;
            return this;
        }

        public Builder dataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry) {
            this.dataLoaderRegistry = dataLoaderRegistry;
            return this;
        }

        public Builder cacheControl(CacheControl cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
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
            this.variables = variables;
            return this;
        }

        public Builder queryDirectives(QueryDirectives queryDirectives) {
            this.queryDirectives = queryDirectives;
            return this;
        }

        public Builder normalizedQuery(Supplier<NormalizedQueryFromAst> normalizedQueryFromAstSupplier) {
            this.normalizedQueryFromAstSupplier = normalizedQueryFromAstSupplier;
            return this;
        }

        public DataFetchingEnvironment build() {
            return new DataFetchingEnvironmentImpl(this);
        }
    }
}
