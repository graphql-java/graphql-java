package graphql.schema;

import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.cachecontrol.CacheControl;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * DelegatingDataFetchingEnvironment implements {@link graphql.schema.DataFetchingEnvironment} by delegating
 * to an underlying instance.  You can use this class to wrap the environment and perhaps change
 * values and behavior more easily.
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
public class DelegatingDataFetchingEnvironment implements DataFetchingEnvironment {

    protected final DataFetchingEnvironment delegateEnvironment;

    /**
     * Called to wrap an existing {@link graphql.schema.DataFetchingEnvironment}.
     *
     * @param delegateEnvironment the environment to wrap and delegate all method called to
     */
    public DelegatingDataFetchingEnvironment(DataFetchingEnvironment delegateEnvironment) {
        this.delegateEnvironment = delegateEnvironment;
    }

    public <T> T getSource() {
        return delegateEnvironment.getSource();
    }

    public Map<String, Object> getArguments() {
        return delegateEnvironment.getArguments();
    }

    public boolean containsArgument(String name) {
        return delegateEnvironment.containsArgument(name);
    }

    public <T> T getArgument(String name) {
        return delegateEnvironment.getArgument(name);
    }

    public <T> T getArgumentOrDefault(String name, T defaultValue) {
        return delegateEnvironment.getArgumentOrDefault(name, defaultValue);
    }

    public <T> T getContext() {
        return delegateEnvironment.getContext();
    }

    @Override
    public GraphQLContext getGraphQlContext() {
        return delegateEnvironment.getGraphQlContext();
    }

    public <T> T getLocalContext() {
        return delegateEnvironment.getLocalContext();
    }

    public <T> T getRoot() {
        return delegateEnvironment.getRoot();
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return delegateEnvironment.getFieldDefinition();
    }

    @Deprecated
    public List<Field> getFields() {
        return delegateEnvironment.getFields();
    }

    public MergedField getMergedField() {
        return delegateEnvironment.getMergedField();
    }

    public Field getField() {
        return delegateEnvironment.getField();
    }

    public GraphQLOutputType getFieldType() {
        return delegateEnvironment.getFieldType();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return delegateEnvironment.getExecutionStepInfo();
    }

    public GraphQLType getParentType() {
        return delegateEnvironment.getParentType();
    }

    public GraphQLSchema getGraphQLSchema() {
        return delegateEnvironment.getGraphQLSchema();
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return delegateEnvironment.getFragmentsByName();
    }

    public ExecutionId getExecutionId() {
        return delegateEnvironment.getExecutionId();
    }

    public DataFetchingFieldSelectionSet getSelectionSet() {
        return delegateEnvironment.getSelectionSet();
    }

    public QueryDirectives getQueryDirectives() {
        return delegateEnvironment.getQueryDirectives();
    }

    public <K, V> DataLoader<K, V> getDataLoader(String dataLoaderName) {
        return delegateEnvironment.getDataLoader(dataLoaderName);
    }

    @Override
    public DataLoaderRegistry getDataLoaderRegistry() {
        return delegateEnvironment.getDataLoaderRegistry();
    }

    @Override
    public Locale getLocale() {
        return delegateEnvironment.getLocale();
    }

    public CacheControl getCacheControl() {
        return delegateEnvironment.getCacheControl();
    }

    public OperationDefinition getOperationDefinition() {
        return delegateEnvironment.getOperationDefinition();
    }

    public Document getDocument() {
        return delegateEnvironment.getDocument();
    }

    public Map<String, Object> getVariables() {
        return delegateEnvironment.getVariables();
    }
}
