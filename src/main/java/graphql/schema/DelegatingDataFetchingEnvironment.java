package graphql.schema;

import graphql.GraphQLContext;
import graphql.PublicApi;
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

    @Override
    public <T> T getSource() {
        return delegateEnvironment.getSource();
    }

    @Override
    public Map<String, Object> getArguments() {
        return delegateEnvironment.getArguments();
    }

    @Override
    public boolean containsArgument(String name) {
        return delegateEnvironment.containsArgument(name);
    }

    @Override
    public <T> T getArgument(String name) {
        return delegateEnvironment.getArgument(name);
    }

    @Override
    public <T> T getArgumentOrDefault(String name, T defaultValue) {
        return delegateEnvironment.getArgumentOrDefault(name, defaultValue);
    }

    @Deprecated(since = "2022-04-17")
    @Override
    public <T> T getContext() {
        return delegateEnvironment.getContext();
    }

    @Override
    public GraphQLContext getGraphQlContext() {
        return delegateEnvironment.getGraphQlContext();
    }

    @Override
    public <T> T getLocalContext() {
        return delegateEnvironment.getLocalContext();
    }

    @Override
    public <T> T getRoot() {
        return delegateEnvironment.getRoot();
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return delegateEnvironment.getFieldDefinition();
    }

    @Deprecated(since = "2019-10-07")
    @Override
    public List<Field> getFields() {
        return delegateEnvironment.getFields();
    }

    @Override
    public MergedField getMergedField() {
        return delegateEnvironment.getMergedField();
    }

    @Override
    public Field getField() {
        return delegateEnvironment.getField();
    }

    @Override
    public GraphQLOutputType getFieldType() {
        return delegateEnvironment.getFieldType();
    }

    @Override
    public ExecutionStepInfo getExecutionStepInfo() {
        return delegateEnvironment.getExecutionStepInfo();
    }

    @Override
    public GraphQLType getParentType() {
        return delegateEnvironment.getParentType();
    }

    @Override
    public GraphQLSchema getGraphQLSchema() {
        return delegateEnvironment.getGraphQLSchema();
    }

    @Override
    public Map<String, FragmentDefinition> getFragmentsByName() {
        return delegateEnvironment.getFragmentsByName();
    }

    @Override
    public ExecutionId getExecutionId() {
        return delegateEnvironment.getExecutionId();
    }

    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return delegateEnvironment.getSelectionSet();
    }

    @Override
    public QueryDirectives getQueryDirectives() {
        return delegateEnvironment.getQueryDirectives();
    }

    @Override
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

    @Override
    public OperationDefinition getOperationDefinition() {
        return delegateEnvironment.getOperationDefinition();
    }

    @Override
    public Document getDocument() {
        return delegateEnvironment.getDocument();
    }

    @Override
    public Map<String, Object> getVariables() {
        return delegateEnvironment.getVariables();
    }
}
