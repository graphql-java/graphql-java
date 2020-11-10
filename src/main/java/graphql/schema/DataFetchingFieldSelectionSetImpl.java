package graphql.schema;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.normalized.NormalizedField;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.util.FpKit.newList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Internal
public class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {

    private final static String SEP = "/";

    private final static DataFetchingFieldSelectionSet NOOP = new DataFetchingFieldSelectionSet() {

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
        public List<SelectedField> getFields() {
            return emptyList();
        }

        @Override
        public List<SelectedField> getImmediateFields() {
            return emptyList();
        }

        @Override
        public List<SelectedField> getFields(String fieldGlobPattern, String... fieldGlobPatterns) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String fieldGlobPattern, String... fieldGlobPatterns) {
            return Collections.emptyMap();
        }
    };

    public static DataFetchingFieldSelectionSet newCollector(GraphQLOutputType fieldType, Supplier<NormalizedField> normalizedFieldSupplier) {
        if (!GraphQLTypeUtil.isLeaf(fieldType)) {
            return new DataFetchingFieldSelectionSetImpl(normalizedFieldSupplier);
        } else {
            // we can only collect fields on object types and interfaces and unions.
            return NOOP;
        }
    }

    private final Supplier<NormalizedField> normalizedFieldSupplier;

    private boolean computedValues;
    private List<SelectedField> immediateFields;
    private Map<String, List<SelectedField>> normalisedSelectionSetFields;
    private Set<String> flattenedFieldsForGlobSearching;

    private DataFetchingFieldSelectionSetImpl(Supplier<NormalizedField> normalizedFieldSupplier) {
        this.normalizedFieldSupplier = normalizedFieldSupplier;
        this.computedValues = false;
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

    @Override
    public List<SelectedField> getFields(String fieldGlobPattern, String... fieldGlobPatterns) {
        if (fieldGlobPattern == null || fieldGlobPattern.isEmpty()) {
            return emptyList();
        }
        computeValuesLazily();

        List<String> targetNames = new ArrayList<>();
        for (String flattenedField : flattenedFieldsForGlobSearching) {
            for (String globPattern : mkIterable(fieldGlobPattern, fieldGlobPatterns)) {
                PathMatcher globMatcher = globMatcher(globPattern);
                Path path = Paths.get(flattenedField);
                if (globMatcher.matches(path)) {
                    targetNames.add(flattenedField);
                }
            }
        }

        return targetNames.stream()
                .flatMap(name -> normalisedSelectionSetFields.getOrDefault(name, Collections.emptyList()).stream())
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public List<SelectedField> getFields() {
        computeValuesLazily();
        return normalisedSelectionSetFields.values().stream()
                .flatMap(Collection::stream)
                .collect(toList());
    }

    @Override
    public List<SelectedField> getImmediateFields() {
        computeValuesLazily();
        return immediateFields;
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
        return getFields().stream().collect(Collectors.groupingBy(SelectedField::getResultKey));
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String fieldGlobPattern, String... fieldGlobPatterns) {
        return getFields(fieldGlobPattern, fieldGlobPatterns).stream().collect(Collectors.groupingBy(SelectedField::getResultKey));
    }

    private void computeValuesLazily() {
        if (computedValues) {
            return;
        }
        synchronized (this) {
            if (computedValues) {
                return;
            }
            flattenedFieldsForGlobSearching = new LinkedHashSet<>();
            normalisedSelectionSetFields = new LinkedHashMap<>();
            immediateFields = new ArrayList<>();
            traverseSubSelectedFields(normalizedFieldSupplier.get(), "", "", true);
            computedValues = true;
        }
    }


    private void traverseSubSelectedFields(NormalizedField currentNormalisedField, String qualifiedFieldPrefix, String simpleFieldPrefix, boolean firstLevel) {
        List<NormalizedField> children = currentNormalisedField.getChildren();
        for (NormalizedField normalizedSubSelectedField : children) {

            String typeQualifiedName = mkTypeQualifiedName(normalizedSubSelectedField);
            String simpleName = normalizedSubSelectedField.getName();

            String globQualifiedName = mkFieldGlobName(qualifiedFieldPrefix, typeQualifiedName);
            String globSimpleName = mkFieldGlobName(simpleFieldPrefix, simpleName);

            flattenedFieldsForGlobSearching.add(globQualifiedName);
            // put in entries for the simple names - eg `Invoice.payments/Payment.amount` becomes `payments/amount`
            flattenedFieldsForGlobSearching.add(globSimpleName);

            SelectedFieldImpl selectedField = new SelectedFieldImpl(globSimpleName, globQualifiedName, normalizedSubSelectedField);
            if (firstLevel) {
                immediateFields.add(selectedField);
            }
            normalisedSelectionSetFields.computeIfAbsent(globQualifiedName, newList()).add(selectedField);
            normalisedSelectionSetFields.computeIfAbsent(globSimpleName, newList()).add(selectedField);

            GraphQLFieldDefinition fieldDefinition = normalizedSubSelectedField.getFieldDefinition();
            GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(fieldDefinition.getType());
            if (!GraphQLTypeUtil.isLeaf(unwrappedType)) {
                traverseSubSelectedFields(normalizedSubSelectedField, globQualifiedName, globSimpleName, false);
            }
        }
    }


    private String removeLeadingSlash(String fieldGlobPattern) {
        if (fieldGlobPattern.startsWith(SEP)) {
            fieldGlobPattern = fieldGlobPattern.substring(1);
        }
        return fieldGlobPattern;
    }

    private static String mkTypeQualifiedName(NormalizedField normalizedField) {
        return normalizedField.getObjectType().getName() + "." + normalizedField.getName();
    }

    private static String mkFieldGlobName(String fieldPrefix, String fieldName) {
        return (!fieldPrefix.isEmpty() ? fieldPrefix + SEP : "") + fieldName;
    }

    private static PathMatcher globMatcher(String fieldGlobPattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + fieldGlobPattern);
    }

    private List<String> mkIterable(String fieldGlobPattern, String[] fieldGlobPatterns) {
        List<String> l = new ArrayList<>();
        l.add(fieldGlobPattern);
        Collections.addAll(l, fieldGlobPatterns);
        return l;
    }

    @Override
    public String toString() {
        if (!computedValues) {
            return "notComputed";
        }
        return String.join("\n", flattenedFieldsForGlobSearching);
    }

    private static class SelectedFieldImpl implements SelectedField {

        private final String qualifiedName;
        private final String fullyQualifiedName;
        private final DataFetchingFieldSelectionSet selectionSet;
        private final NormalizedField normalizedField;

        private SelectedFieldImpl(String simpleQualifiedName, String fullyQualifiedName, NormalizedField normalizedField) {
            this.qualifiedName = simpleQualifiedName;
            this.fullyQualifiedName = fullyQualifiedName;
            this.normalizedField = normalizedField;
            this.selectionSet = new DataFetchingFieldSelectionSetImpl(() -> normalizedField);
        }

        private SelectedField mkParent(NormalizedField normalizedField) {
            String parentSimpleQualifiedName = beforeLastSlash(qualifiedName);
            String parentFullyQualifiedName = beforeLastSlash(fullyQualifiedName);
            return normalizedField.getParent() == null ? null :
                    new SelectedFieldImpl(parentSimpleQualifiedName, parentFullyQualifiedName, normalizedField.getParent());
        }

        private String beforeLastSlash(String name) {
            int index = name.lastIndexOf("/");
            if (index > 0) {
                return name.substring(0, index);
            }
            return "";
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
        public String getFullyQualifiedName() {
            return fullyQualifiedName;
        }

        @Override
        public GraphQLObjectType getObjectType() {
            return normalizedField.getObjectType();
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
        public SelectedField getParentField() {
            // lazy
            return mkParent(normalizedField);
        }

        @Override
        public DataFetchingFieldSelectionSet getSelectionSet() {
            return selectionSet;
        }

        @Override
        public String toString() {
            return getQualifiedName();
        }
    }
}
