package graphql.schema;


import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import org.dataloader.DataLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

@SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
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
    private final DataFetchingFieldSelectionSet selectionSet;
    private final ExecutionStepInfo executionStepInfo;
    private ExecutionContext executionContext;

    public DataFetchingEnvironmentImpl(Object source,
                                       Map<String, Object> arguments,
                                       Object context,
                                       Object root,
                                       GraphQLFieldDefinition fieldDefinition,
                                       List<Field> fields,
                                       GraphQLOutputType fieldType,
                                       GraphQLType parentType,
                                       GraphQLSchema graphQLSchema,
                                       Map<String, FragmentDefinition> fragmentsByName,
                                       ExecutionId executionId,
                                       DataFetchingFieldSelectionSet selectionSet,
                                       ExecutionStepInfo executionStepInfo,
                                       ExecutionContext executionContext) {
        this.source = source;
        this.arguments = arguments == null ? Collections.emptyMap() : arguments;
        this.fragmentsByName = fragmentsByName == null ? Collections.emptyMap() : fragmentsByName;
        this.context = context;
        this.root = root;
        this.fieldDefinition = fieldDefinition;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
        this.executionId = executionId;
        this.selectionSet = selectionSet;
        this.executionStepInfo = executionStepInfo;
        this.executionContext = assertNotNull(executionContext);
    }

    @Override
    public <T> T getSource() {
        return (T) source;
    }

    @Override
    public Map<String, Object> getArguments() {
        return new HashMap<>(arguments);
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
    public Field getField() {
        return fields.get(0);
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
        return new HashMap<>(fragmentsByName);
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
    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    @Override
    public <K, V> DataLoader<K, V> getDataLoader(String dataLoaderName) {
        return executionContext.getDataLoaderRegistry().getDataLoader(dataLoaderName);
    }

    @Override
    public String toString() {
        return "DataFetchingEnvironmentImpl{" +
                "executionStepInfo=" + executionStepInfo +
                '}';
    }
}
