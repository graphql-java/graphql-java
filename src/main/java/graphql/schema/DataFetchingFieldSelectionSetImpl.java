package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.normalized.GraphQlNormalizedField;
import graphql.util.LockKit;

import java.io.File;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.util.FpKit.newList;

@Internal
public class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {

    private final static String SEP = "/";
    private final static boolean UNIXY = SEP.equals(File.separator);

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
            return ImmutableKit.emptyList();
        }

        @Override
        public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
            return ImmutableKit.emptyMap();
        }

        @Override
        public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String fieldGlobPattern, String... fieldGlobPatterns) {
            return ImmutableKit.emptyMap();
        }
    };

    public static DataFetchingFieldSelectionSet newCollector(GraphQLSchema schema, GraphQLOutputType fieldType, Supplier<GraphQlNormalizedField> normalizedFieldSupplier) {
        if (!GraphQLTypeUtil.isLeaf(fieldType)) {
            return new DataFetchingFieldSelectionSetImpl(normalizedFieldSupplier, schema);
        } else {
            // we can only collect fields on object types and interfaces and unions.
            return NOOP;
        }
    }

    private final Supplier<GraphQlNormalizedField> normalizedFieldSupplier;

    private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
    private volatile boolean computedValues;
    private volatile boolean computedImmediateValues;

    // we have multiple entries in this map so that we can do glob matching in multiple ways
    // however it needs to be normalised back to a set of unique fields when give back out to
    // the caller.
    private Map<String, List<SelectedField>> normalisedSelectionSetFields;
    private List<SelectedField> immediateFields;
    private Set<String> flattenedFieldsForGlobSearching;
    private final GraphQLSchema schema;

    private DataFetchingFieldSelectionSetImpl(Supplier<GraphQlNormalizedField> normalizedFieldSupplier, GraphQLSchema schema) {
        this.schema = schema;
        this.normalizedFieldSupplier = normalizedFieldSupplier;
    }

    @Override
    public boolean contains(String fieldGlobPattern) {
        if (fieldGlobPattern == null || fieldGlobPattern.isEmpty()) {
            return false;
        }
        computeValuesLazily(false);
        fieldGlobPattern = removeLeadingSlash(fieldGlobPattern);
        PathMatcher globMatcher = globMatcher(fieldGlobPattern);
        for (String flattenedField : flattenedFieldsForGlobSearching) {
            flattenedField = osAppropriate(flattenedField);
            Path path = Paths.get(flattenedField);
            if (globMatcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private String osAppropriate(String flattenedField) {
        if (UNIXY) {
            return flattenedField;
        } else {
            return flattenedField.replace(SEP, "\\");
        }
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
        computeValuesLazily(false);

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

        return toSetSemanticsList(targetNames.stream()
                .flatMap(name -> normalisedSelectionSetFields.getOrDefault(name, emptyList()).stream()));
    }

    @Override
    public List<SelectedField> getFields() {
        computeValuesLazily(false);
        return toSetSemanticsList(normalisedSelectionSetFields.values().stream()
                .flatMap(Collection::stream));
    }

    private List<SelectedField> toSetSemanticsList(Stream<SelectedField> stream) {
        return ImmutableList.copyOf(stream
                .collect(ImmutableSet.toImmutableSet()));
    }

    @Override
    public List<SelectedField> getImmediateFields() {
        computeValuesLazily(true);
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

    private void computeValuesLazily(boolean immediate) {
        if (computedValues) {
            return;
        }

        // Avoid recomputing the immediate fields if they have already been computed.
        if (immediate && computedImmediateValues) {
            return;
        }

        // this supplier is a once only thread synced call - so do it outside this lock
        // if only to have only 1 lock in action at a time
        GraphQlNormalizedField currentNormalisedField = normalizedFieldSupplier.get();

        lock.runLocked(() -> {
            if (computedValues) {
                return;
            }

            if (computedImmediateValues && immediate) {
                return;
            }

            flattenedFieldsForGlobSearching = new LinkedHashSet<>();
            normalisedSelectionSetFields = new LinkedHashMap<>();
            ImmutableList.Builder<SelectedField> immediateFieldsBuilder = ImmutableList.builder();
            traverseSubSelectedFields(currentNormalisedField, immediateFieldsBuilder, "", "", true, immediate);
            immediateFields = immediateFieldsBuilder.build();
            computedImmediateValues = true;
            computedValues = !immediate;
        });
    }

    private void traverseSubSelectedFields(GraphQlNormalizedField currentNormalisedField, ImmutableList.Builder<SelectedField> immediateFieldsBuilder, String qualifiedFieldPrefix, String simpleFieldPrefix, boolean firstLevel, boolean immediate) {
        List<GraphQlNormalizedField> children = currentNormalisedField.getGraphQlNormalizedChildren();
        for (GraphQlNormalizedField normalizedSubSelectedField : children) {
            String typeQualifiedName = mkTypeQualifiedName(normalizedSubSelectedField);
            String simpleName = normalizedSubSelectedField.getName();

            String globQualifiedName = mkFieldGlobName(qualifiedFieldPrefix, typeQualifiedName);
            String globSimpleName = mkFieldGlobName(simpleFieldPrefix, simpleName);

            flattenedFieldsForGlobSearching.add(globQualifiedName);
            // put in entries for the simple names - eg `Invoice.payments/Payment.amount` becomes `payments/amount`
            flattenedFieldsForGlobSearching.add(globSimpleName);

            SelectedFieldImpl selectedField = new SelectedFieldImpl(globSimpleName, globQualifiedName, normalizedSubSelectedField, schema);
            if (firstLevel) {
                immediateFieldsBuilder.add(selectedField);
            }
            normalisedSelectionSetFields.computeIfAbsent(globQualifiedName, newList()).add(selectedField);
            normalisedSelectionSetFields.computeIfAbsent(globSimpleName, newList()).add(selectedField);

            if (normalizedSubSelectedField.hasChildren() && !immediate) {
                traverseSubSelectedFields(normalizedSubSelectedField, immediateFieldsBuilder, globQualifiedName, globSimpleName, false, false);
            }
        }
    }


    private String removeLeadingSlash(String fieldGlobPattern) {
        if (fieldGlobPattern.startsWith(SEP)) {
            fieldGlobPattern = fieldGlobPattern.substring(1);
        }
        return fieldGlobPattern;
    }

    private static String mkTypeQualifiedName(GraphQlNormalizedField executableNormalizedField) {
        return executableNormalizedField.objectTypeNamesToString() + "." + executableNormalizedField.getName();
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
        private final GraphQlNormalizedField executableNormalizedField;
        private final GraphQLSchema schema;

        private SelectedFieldImpl(String simpleQualifiedName, String fullyQualifiedName, GraphQlNormalizedField executableNormalizedField, GraphQLSchema schema) {
            this.schema = schema;
            this.qualifiedName = simpleQualifiedName;
            this.fullyQualifiedName = fullyQualifiedName;
            this.executableNormalizedField = executableNormalizedField;
            this.selectionSet = new DataFetchingFieldSelectionSetImpl(() -> executableNormalizedField, schema);
        }

        private SelectedField mkParent(GraphQlNormalizedField executableNormalizedField) {
            String parentSimpleQualifiedName = beforeLastSlash(qualifiedName);
            String parentFullyQualifiedName = beforeLastSlash(fullyQualifiedName);
            return executableNormalizedField.getGraphQlNormalizedParent() == null ? null :
                    new SelectedFieldImpl(parentSimpleQualifiedName, parentFullyQualifiedName, executableNormalizedField.getGraphQlNormalizedParent(), schema);
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
            return executableNormalizedField.getName();
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
        public List<GraphQLFieldDefinition> getFieldDefinitions() {
            return executableNormalizedField.getFieldDefinitions(schema);
        }

        @Override
        public GraphQLOutputType getType() {
            return executableNormalizedField.getType(schema);
        }

        @Override
        public List<GraphQLObjectType> getObjectTypes() {
            return this.schema.getTypes(executableNormalizedField.getObjectTypeNames());
        }

        @Override
        public List<String> getObjectTypeNames() {
            return ImmutableList.copyOf(executableNormalizedField.getObjectTypeNames());
        }

        @Override
        public Map<String, Object> getArguments() {
            return executableNormalizedField.getResolvedArguments();
        }

        @Override
        public int getLevel() {
            return executableNormalizedField.getLevel();
        }

        @Override
        public boolean isConditional() {
            return executableNormalizedField.isConditional(this.schema);
        }

        @Override
        public String getAlias() {
            return executableNormalizedField.getAlias();
        }

        @Override
        public String getResultKey() {
            return executableNormalizedField.getResultKey();
        }

        @Override
        public SelectedField getParentField() {
            // lazy
            return mkParent(executableNormalizedField);
        }

        @Override
        public DataFetchingFieldSelectionSet getSelectionSet() {
            return selectionSet;
        }

        // a selected field is the same as another selected field if it's the same ExecutableNF
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SelectedFieldImpl that = (SelectedFieldImpl) o;
            return executableNormalizedField.equals(that.executableNormalizedField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executableNormalizedField);
        }

        @Override
        public String toString() {
            return getFullyQualifiedName();
        }
    }
}
