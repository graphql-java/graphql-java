package graphql.schema;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

@Internal
public class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {

    private final static DataFetchingFieldSelectionSet NOOP = new DataFetchingFieldSelectionSet() {
        @Override
        public Map<String, List<Field>> get() {
            return emptyMap();
        }

        @Override
        public Map<String, Map<String, Object>> getArguments() {
            return emptyMap();
        }

        @Override
        public Map<String, GraphQLFieldDefinition> getDefinitions() {
            return emptyMap();
        }
    };

    public static DataFetchingFieldSelectionSet newCollector(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields) {
        GraphQLType unwrappedType = ExecutionTypeInfo.unwrapBaseType(fieldType);
        if (unwrappedType instanceof GraphQLFieldsContainer) {
            return new DataFetchingFieldSelectionSetImpl(executionContext, (GraphQLFieldsContainer) unwrappedType, fields);
        } else {
            // we can only collect fields on object types and interfaces.  Scalars, Unions etc... cant be done.
            return NOOP;
        }
    }

    private static GraphQLObjectType asObjectTypeOrNull(GraphQLType unwrappedType) {
        return unwrappedType instanceof GraphQLObjectType ? (GraphQLObjectType) unwrappedType : null;
    }

    private final FieldCollectorParameters parameters;
    private final List<Field> parentFields;
    private final GraphQLSchema graphQLSchema;
    private final GraphQLFieldsContainer parentFieldType;
    private final Map<String, Object> variables;

    private Map<String, List<Field>> selectionSetFields;
    private Map<String, GraphQLFieldDefinition> selectionSetFieldDefinitions;
    private Map<String, Map<String, Object>> selectionSetFieldArgs;

    private DataFetchingFieldSelectionSetImpl(ExecutionContext executionContext, GraphQLFieldsContainer parentFieldType, List<Field> parentFields) {
        this.parentFields = parentFields;
        this.graphQLSchema = executionContext.getGraphQLSchema();
        this.parentFieldType = parentFieldType;
        this.variables = executionContext.getVariables();
        this.parameters = FieldCollectorParameters.newParameters()
                .schema(graphQLSchema)
                .objectType(asObjectTypeOrNull(parentFieldType))
                .fragments(executionContext.getFragmentsByName())
                .variables(variables)
                .build();
    }


    @Override
    public Map<String, List<Field>> get() {
        // by having a .get() method we get lazy evaluation.
        computeValuesLazily();
        return selectionSetFields;
    }

    public Map<String, Map<String, Object>> getArguments() {
        computeValuesLazily();
        return selectionSetFieldArgs;
    }

    public Map<String, GraphQLFieldDefinition> getDefinitions() {
        computeValuesLazily();
        return selectionSetFieldDefinitions;
    }

    private void computeValuesLazily() {
        synchronized (this) {
            if (selectionSetFields != null) {
                return;
            }
            FieldCollector fieldCollector = new FieldCollector();
            ValuesResolver valuesResolver = new ValuesResolver();

            selectionSetFields = fieldCollector.collectFields(parameters, parentFields);
            selectionSetFieldDefinitions = new LinkedHashMap<>();
            selectionSetFieldArgs = new LinkedHashMap<>();

            for (String fieldName : selectionSetFields.keySet()) {
                Field field = selectionSetFields.get(fieldName).get(0);
                GraphQLFieldDefinition fieldDef = Introspection.getFieldDef(graphQLSchema, (GraphQLCompositeType) parentFieldType, fieldName);
                Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), variables);

                selectionSetFieldArgs.put(fieldName, argumentValues);
                selectionSetFieldDefinitions.put(fieldName, fieldDef);
            }
        }
    }
}
