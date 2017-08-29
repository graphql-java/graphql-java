package graphql.schema;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.language.Field;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {

    private final static DataFetchingFieldSelectionSet NOOP = Collections::emptyMap;

    public static DataFetchingFieldSelectionSet newCollector(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields) {
        GraphQLType unwrappedType = ExecutionTypeInfo.unwrapBaseType(fieldType);
        if (unwrappedType instanceof GraphQLFieldsContainer) {
            return new DataFetchingFieldSelectionSetImpl(executionContext, (GraphQLFieldsContainer) unwrappedType, fields);
        } else {
            // we can only collect fields on object types.  Scalars, Interfaces, Unions etc... cant be done.
            // we will be called back once they are resolved however
            return NOOP;
        }
    }

    private final FieldCollector fieldCollector;
    private final FieldCollectorParameters parameters;
    private final List<Field> fields;

    private DataFetchingFieldSelectionSetImpl(ExecutionContext executionContext, GraphQLFieldsContainer fieldsContainer, List<Field> fields) {
        this.fields = fields;
        this.fieldCollector = new FieldCollector();
        this.parameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .fieldsContainer(fieldsContainer)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
    }

    @Override
    public Map<String, List<Field>> get() {
        // by having a .get() method we get lazy evaluation.  
        return fieldCollector.collectFields(parameters, fields);
    }
}
