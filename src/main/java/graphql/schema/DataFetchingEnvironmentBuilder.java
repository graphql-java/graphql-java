package graphql.schema;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.FieldDirectives;
import graphql.execution.directives.FieldDirectivesImpl;
import graphql.language.FragmentDefinition;

import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * A builder of {@link DataFetchingEnvironment}s
 */
@PublicApi
public class DataFetchingEnvironmentBuilder {


    /**
     * @return a new {@link DataFetchingEnvironmentBuilder}
     */
    public static DataFetchingEnvironmentBuilder newDataFetchingEnvironment() {
        return new DataFetchingEnvironmentBuilder();
    }

    public static DataFetchingEnvironmentBuilder newDataFetchingEnvironment(DataFetchingEnvironment environment) {
        return new DataFetchingEnvironmentBuilder()
                .source(environment.getSource())
                .arguments(environment.getArguments())
                .context(environment.getContext())
                .localContext(environment.getLocalContext())
                .root(environment.getRoot())
                .fieldDefinition(environment.getFieldDefinition())
                .mergedField(environment.getMergedField())
                .fieldType(environment.getFieldType())
                .executionStepInfo(environment.getExecutionStepInfo())
                .parentType(environment.getParentType())
                .graphQLSchema(environment.getGraphQLSchema())
                .fragmentsByName(environment.getFragmentsByName())
                .executionId(environment.getExecutionId())
                .selectionSet(environment.getSelectionSet())
                .fieldDirectives(environment.getFieldDirectives())
                .executionContext(environment.getExecutionContext())
                ;
    }

    public static DataFetchingEnvironmentBuilder newDataFetchingEnvironment(ExecutionContext executionContext) {
        return new DataFetchingEnvironmentBuilder()
                .context(executionContext.getContext())
                .root(executionContext.getRoot())
                .graphQLSchema(executionContext.getGraphQLSchema())
                .fragmentsByName(executionContext.getFragmentsByName())
                .executionId(executionContext.getExecutionId())
                .executionContext(executionContext);

    }


    private Object source;
    private Map<String, Object> arguments = emptyMap();
    private Object context;
    private Object localContext;
    private Object root;
    private GraphQLFieldDefinition fieldDefinition;
    private MergedField mergedField;
    private GraphQLOutputType fieldType;
    private GraphQLType parentType;
    private GraphQLSchema graphQLSchema;
    private Map<String, FragmentDefinition> fragmentsByName = emptyMap();
    private ExecutionId executionId;
    private DataFetchingFieldSelectionSet selectionSet;
    private FieldDirectives fieldDirectives = new FieldDirectivesImpl(emptyList());
    private ExecutionStepInfo executionStepInfo;
    private ExecutionContext executionContext;

    public DataFetchingEnvironmentBuilder source(Object source) {
        this.source = source;
        return this;
    }

    public DataFetchingEnvironmentBuilder arguments(Map<String, Object> arguments) {
        this.arguments = arguments;
        return this;
    }

    public DataFetchingEnvironmentBuilder context(Object context) {
        this.context = context;
        return this;
    }

    public DataFetchingEnvironmentBuilder localContext(Object localContext) {
        this.localContext = localContext;
        return this;
    }

    public DataFetchingEnvironmentBuilder root(Object root) {
        this.root = root;
        return this;
    }

    public DataFetchingEnvironmentBuilder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
        return this;
    }

    public DataFetchingEnvironmentBuilder mergedField(MergedField mergedField) {
        this.mergedField = mergedField;
        return this;
    }

    public DataFetchingEnvironmentBuilder fieldType(GraphQLOutputType fieldType) {
        this.fieldType = fieldType;
        return this;
    }

    public DataFetchingEnvironmentBuilder parentType(GraphQLType parentType) {
        this.parentType = parentType;
        return this;
    }

    public DataFetchingEnvironmentBuilder graphQLSchema(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
        return this;
    }

    public DataFetchingEnvironmentBuilder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
        return this;
    }

    public DataFetchingEnvironmentBuilder executionId(ExecutionId executionId) {
        this.executionId = executionId;
        return this;
    }

    public DataFetchingEnvironmentBuilder selectionSet(DataFetchingFieldSelectionSet selectionSet) {
        this.selectionSet = selectionSet;
        return this;
    }

    public DataFetchingEnvironmentBuilder fieldDirectives(FieldDirectives fieldDirectives) {
        this.fieldDirectives = fieldDirectives;
        return this;
    }

    public DataFetchingEnvironmentBuilder executionStepInfo(ExecutionStepInfo executionStepInfo) {
        this.executionStepInfo = executionStepInfo;
        return this;
    }

    public DataFetchingEnvironmentBuilder executionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        return this;
    }

    public DataFetchingEnvironment build() {
        return new DataFetchingEnvironmentImpl(source, arguments, context, localContext, root,
                fieldDefinition, mergedField, fieldType, parentType, graphQLSchema, fragmentsByName, executionId, selectionSet,
                fieldDirectives, executionStepInfo,
                executionContext);
    }
}
