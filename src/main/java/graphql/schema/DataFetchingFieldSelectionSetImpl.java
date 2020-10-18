package graphql.schema;

import graphql.Internal;
import graphql.execution.FieldCollector;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.ValuesResolver;
import graphql.normalized.NormalizedField;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Internal
public class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {

    private final static String SEP = "/";

    private final static DataFetchingFieldSelectionSet NOOP = new DataFetchingFieldSelectionSet() {
        @Override
        public MergedSelectionSet get() {
            return MergedSelectionSet.newMergedSelectionSet().build();
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

        @Override
        public boolean containsAnyOf(String fieldGlobPattern, String... fieldGlobPatterns) {
            return false;
        }

        @Override
        public boolean containsAllOf(String fieldGlobPattern, String... fieldGlobPatterns) {
            return false;
        }

        @Override
        public SelectedField getField(String fieldName) {
            return null;
        }

        @Override
        public List<SelectedField> getFields() {
            return emptyList();
        }

        @Override
        public List<SelectedField> getFields(String fieldGlobPattern) {
            return emptyList();
        }
    };

    public static DataFetchingFieldSelectionSet newCollector(GraphQLOutputType fieldType, Supplier<NormalizedField> normalizedFieldSupplier) {
        GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(fieldType);
        if (!GraphQLTypeUtil.isLeaf(fieldType)) {
            return new DataFetchingFieldSelectionSetImpl(normalizedFieldSupplier);
        } else {
            // we can only collect fields on object types and interfaces and unions.
            return NOOP;
        }
    }

    private static GraphQLObjectType asObjectTypeOrNull(GraphQLType unwrappedType) {
        return unwrappedType instanceof GraphQLObjectType ? (GraphQLObjectType) unwrappedType : null;
    }

    private final FieldCollector fieldCollector = new FieldCollector();
    private final ValuesResolver valuesResolver = new ValuesResolver();

    private final Supplier<NormalizedField> normalizedFieldSupplier;

    private boolean computedValues;
    private Map<String, MergedField> selectionSetFields;
    private Map<String, SelectedField> normalisedSelectionSetFields;
    private Map<String, GraphQLFieldDefinition> selectionSetFieldDefinitions;
    private Map<String, Map<String, Object>> selectionSetFieldArgs;
    private Set<String> flattenedFieldsForGlobSearching;

    private DataFetchingFieldSelectionSetImpl(Supplier<NormalizedField> normalizedFieldSupplier) {
        this.normalizedFieldSupplier = normalizedFieldSupplier;
        this.computedValues = false;
    }

    @Override
    public MergedSelectionSet get() {
        // by having a .get() method we get lazy evaluation.
        computeValuesLazily();
        return MergedSelectionSet.newMergedSelectionSet().subFields(selectionSetFields).build();
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
        fieldGlobPattern = removeLeadingSlash(fieldGlobPattern);
        PathMatcher globMatcher = globMatcher(fieldGlobPattern);
        for (String flattenedField : flattenedFieldsForGlobSearching) {
            Path path = Paths.get(flattenedField);
            if (globMatcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAnyOf(String fieldGlobPattern, String... fieldGlobPatterns) {
        assertNotNull(fieldGlobPattern);
        assertNotNull(fieldGlobPatterns);
        for (String globPattern : mkIterable(fieldGlobPattern, fieldGlobPatterns)) {
            if (contains(globPattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAllOf(String fieldGlobPattern, String... fieldGlobPatterns) {
        assertNotNull(fieldGlobPattern);
        assertNotNull(fieldGlobPatterns);
        for (String globPattern : mkIterable(fieldGlobPattern, fieldGlobPatterns)) {
            if (!contains(globPattern)) {
                return false;
            }
        }
        return true;
    }

    private List<String> mkIterable(String fieldGlobPattern, String[] fieldGlobPatterns) {
        List<String> l = new ArrayList<>();
        l.add(fieldGlobPattern);
        Collections.addAll(l, fieldGlobPatterns);
        return l;
    }

    @Override
    public SelectedField getField(String fqFieldName) {
        computeValuesLazily();
        return normalisedSelectionSetFields.get(fqFieldName);
    }

    @Override
    public List<SelectedField> getFields(String fieldGlobPattern) {
        if (fieldGlobPattern == null || fieldGlobPattern.isEmpty()) {
            return emptyList();
        }
        computeValuesLazily();

        List<String> targetNames = new ArrayList<>();
        PathMatcher globMatcher = globMatcher(fieldGlobPattern);
        for (String flattenedField : flattenedFieldsForGlobSearching) {
            Path path = Paths.get(flattenedField);
            if (globMatcher.matches(path)) {
                targetNames.add(flattenedField);
            }
        }
        return targetNames.stream()
                .map(this::getField)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<SelectedField> getFields() {
        computeValuesLazily();

        return new ArrayList<>(normalisedSelectionSetFields.values());
    }

    private String removeLeadingSlash(String fieldGlobPattern) {
        if (fieldGlobPattern.startsWith(SEP)) {
            fieldGlobPattern = fieldGlobPattern.substring(1);
        }
        return fieldGlobPattern;
    }

    private PathMatcher globMatcher(String fieldGlobPattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + fieldGlobPattern);
    }

    private void computeValuesLazily() {
        if (computedValues) {
            return;
        }
        synchronized (this) {
            if (computedValues) {
                return;
            }
            selectionSetFields = new LinkedHashMap<>();
            selectionSetFieldDefinitions = new LinkedHashMap<>();
            selectionSetFieldArgs = new LinkedHashMap<>();
            flattenedFieldsForGlobSearching = new LinkedHashSet<>();
            normalisedSelectionSetFields = new LinkedHashMap<>();
            traverseSubSelectedFields(normalizedFieldSupplier.get(), "", "");
            computedValues = true;
        }
    }


    private void traverseSubSelectedFields(NormalizedField currentNormalisedField, String qualifiedFieldPrefix, String simpleFieldPrefix) {
        Map<String, List<NormalizedField>> canonicalNameMap = currentNormalisedField.getChildren()
                .stream().collect(Collectors.groupingBy(this::mkCanonicalName));

        for (NormalizedField normalizedSubSelectedField : currentNormalisedField.getChildren()) {
            GraphQLFieldDefinition fieldDefinition = normalizedSubSelectedField.getFieldDefinition();

            String canonicalName = mkCanonicalName(normalizedSubSelectedField);
            String typeQualifiedName = mkTypeQualifiedName(normalizedSubSelectedField);
            String simpleName = normalizedSubSelectedField.getName();

            String globAliasedQualifiedName = mkFieldGlobName(qualifiedFieldPrefix, canonicalName);
            String globQualifiedName = mkFieldGlobName(qualifiedFieldPrefix, typeQualifiedName);
            String globSimpleName = mkFieldGlobName(simpleFieldPrefix, simpleName);


            flattenedFieldsForGlobSearching.add(globQualifiedName);
            flattenedFieldsForGlobSearching.add(globAliasedQualifiedName);
            selectionSetFieldArgs.put(globQualifiedName, normalizedSubSelectedField.getArguments());
            selectionSetFieldArgs.put(globAliasedQualifiedName, normalizedSubSelectedField.getArguments());
            selectionSetFieldDefinitions.put(globQualifiedName, fieldDefinition);
            selectionSetFieldDefinitions.put(globQualifiedName, fieldDefinition);
            normalisedSelectionSetFields.put(globQualifiedName, new SelectedFieldImpl(globQualifiedName, normalizedSubSelectedField));

            // put in entries for the simple names - eg `Invoice.payments/Payment.amount` becomes `payments/amount`
            flattenedFieldsForGlobSearching.add(globSimpleName);

            List<NormalizedField> normalizedFields = canonicalNameMap.get(canonicalName);
            // we can only put in args and field def if there is ONLY on thing possible
            if (normalizedFields.size() == 1) {
                selectionSetFieldArgs.put(globSimpleName, normalizedSubSelectedField.getArguments());
                selectionSetFieldDefinitions.put(globSimpleName, fieldDefinition);
                normalisedSelectionSetFields.put(globSimpleName, new SelectedFieldImpl(globSimpleName, normalizedSubSelectedField));
            }

            GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(fieldDefinition.getType());
            if (!GraphQLTypeUtil.isLeaf(unwrappedType)) {
                traverseSubSelectedFields(normalizedSubSelectedField, globQualifiedName, globSimpleName);
            }
        }
    }

    private String mkTypeQualifiedName(NormalizedField normalizedField) {
        return normalizedField.getObjectType().getName() + "." + normalizedField.getName();
    }

    private String mkAliasPrefix(NormalizedField normalizedField) {
        return normalizedField.getAlias() == null ? "" : normalizedField.getAlias() + ":";
    }

    private String mkCanonicalName(NormalizedField normalizedField) {
        return mkAliasPrefix(normalizedField) + mkTypeQualifiedName(normalizedField);
    }

    private String mkFieldGlobName(String fieldPrefix, String fieldName) {
        return (!fieldPrefix.isEmpty() ? fieldPrefix + SEP : "") + fieldName;
    }

    @Override
    public String toString() {
        if (!computedValues) {
            return "notcomputed";
        }
        return flattenedFieldsForGlobSearching.toString();
    }

    private static class SelectedFieldImpl implements SelectedField {

        private final String qualifiedName;
        private final DataFetchingFieldSelectionSet selectionSet;
        private final NormalizedField normalizedField;

        private SelectedFieldImpl(String qualifiedName, NormalizedField normalizedField) {
            this.qualifiedName = qualifiedName;
            this.normalizedField = normalizedField;
            this.selectionSet = new DataFetchingFieldSelectionSetImpl(() -> normalizedField);
        }

        @Override
        public String getName() {
            return normalizedField.getName();
        }

        @Override
        public String getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public GraphQLFieldDefinition getFieldDefinition() {
            return normalizedField.getFieldDefinition();
        }

        @Override
        public Map<String, Object> getArguments() {
            return normalizedField.getArguments();
        }

        @Override
        public int getLevel() {
            return normalizedField.getLevel();
        }

        @Override
        public boolean isConditional() {
            return normalizedField.isConditional();
        }

        @Override
        public String getAlias() {
            return normalizedField.getAlias();
        }

        @Override
        public String getResultKey() {
            return normalizedField.getResultKey();
        }

        @Override
        public DataFetchingFieldSelectionSet getSelectionSet() {
            return selectionSet;
        }
    }
}
