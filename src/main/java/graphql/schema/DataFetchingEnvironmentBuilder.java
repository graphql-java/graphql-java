package graphql.schema;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionTypeInfo;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
                .root(environment.getRoot())
                .fields(environment.getFields())
                .fieldType(environment.getFieldType())
                .fieldTypeInfo(environment.getFieldTypeInfo())
                .parentType(environment.getParentType())
                .graphQLSchema(environment.getGraphQLSchema())
                .fragmentsByName(environment.getFragmentsByName())
                .executionId(environment.getExecutionId())
                .selectionSet(environment.getSelectionSet())
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
    private Map<String, Object> arguments = Collections.emptyMap();
    private Object context;
    private Object root;
    private GraphQLFieldDefinition fieldDefinition;
    private List<Field> fields = Collections.emptyList();
    private GraphQLOutputType fieldType;
    private GraphQLType parentType;
    private GraphQLSchema graphQLSchema;
    private Map<String, FragmentDefinition> fragmentsByName = Collections.emptyMap();
    private ExecutionId executionId;
    private DataFetchingFieldSelectionSet selectionSet;
    private ExecutionTypeInfo typeInfo;
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

    public DataFetchingEnvironmentBuilder root(Object root) {
        this.root = root;
        return this;
    }

    public DataFetchingEnvironmentBuilder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
        return this;
    }

    public DataFetchingEnvironmentBuilder fields(List<Field> fields) {
        this.fields = fields;
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

    public DataFetchingEnvironmentBuilder fieldTypeInfo(ExecutionTypeInfo typeInfo) {
        this.typeInfo = typeInfo;
        return this;
    }

    public DataFetchingEnvironmentBuilder executionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        return this;
    }

    public DataFetchingEnvironment build() {
        return new DataFetchingEnvironmentImpl(source, arguments, context, root,
                fieldDefinition, fields, fieldType, parentType, graphQLSchema, fragmentsByName, executionId, selectionSet,
                typeInfo,
                executionContext);
    }
}
