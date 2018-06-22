package graphql.schema;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        @Override
        public boolean contains(String fieldGlobPattern) {
            return false;
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

    private final List<Field> parentFields;
    private final GraphQLSchema graphQLSchema;
    private final GraphQLFieldsContainer parentFieldType;
    private final Map<String, Object> variables;
    private final Map<String, FragmentDefinition> fragmentsByName;

    private Map<String, List<Field>> selectionSetFields;
    private Map<String, GraphQLFieldDefinition> selectionSetFieldDefinitions;
    private Map<String, Map<String, Object>> selectionSetFieldArgs;
    private Set<String> flattenedFields;

    private DataFetchingFieldSelectionSetImpl(ExecutionContext executionContext, GraphQLFieldsContainer parentFieldType, List<Field> parentFields) {
        this.parentFields = parentFields;
        this.graphQLSchema = executionContext.getGraphQLSchema();
        this.parentFieldType = parentFieldType;
        this.variables = executionContext.getVariables();
        this.fragmentsByName = executionContext.getFragmentsByName();
    }


    @Override
    public Map<String, List<Field>> get() {
        // by having a .get() method we get lazy evaluation.
        computeValuesLazily();
        return selectionSetFields;
    }

    @Override
    public Map<String, Map<String, Object>> getArguments() {
        computeValuesLazily();
        return selectionSetFieldArgs;
    }

    @Override
    public Map<String, GraphQLFieldDefinition> getDefinitions() {
        computeValuesLazily();
        return selectionSetFieldDefinitions;
    }

    @Override
    public boolean contains(String fieldGlobPattern) {
        if (fieldGlobPattern == null || fieldGlobPattern.isEmpty()) {
            return false;
        }
        computeValuesLazily();
        PathMatcher globMatcher = FileSystems.getDefault().getPathMatcher("glob:" + fieldGlobPattern);
        for (String flattenedField : flattenedFields) {
            Path path = Paths.get(flattenedField);
            if (globMatcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private void computeValuesLazily() {
        synchronized (this) {
            if (selectionSetFields != null) {
                return;
            }

            selectionSetFields = new LinkedHashMap<>();
            selectionSetFieldDefinitions = new LinkedHashMap<>();
            selectionSetFieldArgs = new LinkedHashMap<>();
            flattenedFields = new LinkedHashSet<>();

            traverseFields(parentFields, parentFieldType, "");
        }
    }

    private final static String SEP = "/";


    private void traverseFields(List<Field> fieldList, GraphQLFieldsContainer parentFieldType, String fieldPrefix) {
        FieldCollector fieldCollector = new FieldCollector();
        ValuesResolver valuesResolver = new ValuesResolver();

        FieldCollectorParameters parameters = FieldCollectorParameters.newParameters()
                .schema(graphQLSchema)
                .objectType(asObjectTypeOrNull(parentFieldType))
                .fragments(fragmentsByName)
                .variables(variables)
                .build();

        Map<String, List<Field>> collectedFields = fieldCollector.collectFields(parameters, fieldList);
        for (Map.Entry<String, List<Field>> entry : collectedFields.entrySet()) {
            String fieldName = mkFieldName(fieldPrefix, entry.getKey());
            List<Field> collectedFieldList = entry.getValue();
            selectionSetFields.put(fieldName, collectedFieldList);

            Field field = collectedFieldList.get(0);
            GraphQLFieldDefinition fieldDef = Introspection.getFieldDef(graphQLSchema, (GraphQLCompositeType) parentFieldType, field.getName());
            GraphQLType unwrappedType = ExecutionTypeInfo.unwrapBaseType(fieldDef.getType());
            Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), variables);

            selectionSetFieldArgs.put(fieldName, argumentValues);
            selectionSetFieldDefinitions.put(fieldName, fieldDef);
            flattenedFields.add(fieldName);

            if (unwrappedType instanceof GraphQLFieldsContainer) {
                traverseFields(collectedFieldList, (GraphQLFieldsContainer) unwrappedType, fieldName);
            }
        }
    }

    private String mkFieldName(String fieldPrefix, String fieldName) {
        return (!fieldPrefix.isEmpty() ? fieldPrefix + SEP : "") + fieldName;
    }
}
