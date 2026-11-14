package graphql.schema;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.normalized.NormalizedDocument;
import graphql.normalized.NormalizedFieldCollector;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;
import static graphql.util.FpKit.interThreadMemoize;

@Internal
public final class DataFetchingFieldSelectionSetImpl implements DataFetchingFieldSelectionSet {
    private final ExecutionContext context;
    private final List<Field> parentFields;
    private final GraphQLOutputType outputType;
    private final int parentLevel;
    private final @Nullable SelectedField parent;
    private final String simplePrefix;
    private final String typePrefix;
    private final Supplier<List<SelectedField>> immediateFields;
    private final Supplier<List<SelectedField>> allFields;

    public static DataFetchingFieldSelectionSet newCollector(ExecutionContext context, MergedField field, GraphQLOutputType outputType, int parentLevel) {
        if (!context.getGraphQLContext().getBoolean(NormalizedDocument.ENABLED) || context.getNormalizedDocument() == null) {
            return UnavailableFieldSelectionSet.INSTANCE;
        }
        return new DataFetchingFieldSelectionSetImpl(context, field.getFields(), outputType, parentLevel, null, "", "");
    }

    public DataFetchingFieldSelectionSetImpl(ExecutionContext context, List<Field> parentFields, GraphQLOutputType outputType,
                                            int parentLevel, @Nullable SelectedField parent, String simplePrefix, String typePrefix) {
        this.context = context;
        this.parentFields = List.copyOf(parentFields);
        this.outputType = outputType;
        this.parentLevel = parentLevel;
        this.parent = parent;
        this.simplePrefix = simplePrefix;
        this.typePrefix = typePrefix;
        this.immediateFields = interThreadMemoize(this::collectImmediateFields);
        this.allFields = interThreadMemoize(this::collectAllFields);
    }

    private NormalizedDocument requireDocument() {
        NormalizedDocument document = context.getNormalizedDocument();
        if (!context.getGraphQLContext().getBoolean(NormalizedDocument.ENABLED) || document == null) {
            throw UnavailableFieldSelectionSet.unavailable();
        }
        return document;
    }

    private List<SelectedField> collectImmediateFields() {
        NormalizedFieldCollector collector = new NormalizedFieldCollector(requireDocument(), context);
        List<GraphQLObjectType> types = possibleTypes();
        List<SelectedField> fields = new ArrayList<>();
        for (GraphQLObjectType type : types) {
            addFields(fields, collector.collect(parentFields, type), type, types.size() > 1);
        }
        return List.copyOf(fields);
    }

    private List<GraphQLObjectType> possibleTypes() {
        GraphQLType type = GraphQLTypeUtil.unwrapAll(outputType);
        if (type instanceof GraphQLObjectType objectType) {
            return List.of(objectType);
        }
        List<GraphQLObjectType> types = new ArrayList<>();
        if (type instanceof GraphQLInterfaceType interfaceType) {
            types.addAll(context.getGraphQLSchema().getImplementations(interfaceType));
        } else if (type instanceof GraphQLUnionType unionType) {
            unionType.getTypes().forEach(member -> types.add((GraphQLObjectType) member));
        }
        types.sort(Comparator.comparing(GraphQLObjectType::getName));
        return types;
    }

    private void addFields(List<SelectedField> result, List<List<Field>> groups, GraphQLObjectType objectType, boolean conditional) {
        for (List<Field> fields : groups) {
            result.add(new SelectedFieldImpl(context, fields, objectType, parentLevel + 1, conditional, parent, simplePrefix, typePrefix));
        }
    }

    private List<SelectedField> collectAllFields() {
        List<SelectedField> result = new ArrayList<>();
        Deque<SelectedField> pending = new ArrayDeque<>();
        pushReverse(pending, getImmediateFields());
        while (!pending.isEmpty()) {
            SelectedFieldImpl field = (SelectedFieldImpl) pending.pop();
            result.add(field);
            pushReverse(pending, field.getTraversalChildren());
        }
        return List.copyOf(result);
    }

    private void pushReverse(Deque<SelectedField> pending, List<SelectedField> fields) {
        for (int index = fields.size() - 1; index >= 0; index--) {
            pending.push(fields.get(index));
        }
    }

    @Override
    public List<SelectedField> getImmediateFields() {
        requireDocument();
        return immediateFields.get();
    }

    @Override
    public List<SelectedField> getFields() {
        requireDocument();
        return allFields.get();
    }

    @Override
    public List<SelectedField> getFields(String pattern, String... patterns) {
        requireDocument();
        if (pattern == null || pattern.isEmpty()) {
            return List.of();
        }
        List<PathMatcher> matchers = patterns(pattern, patterns).stream().map(this::matcher).toList();
        return getFields().stream().filter(field -> matches(field, matchers)).toList();
    }

    private boolean matches(SelectedField field, List<PathMatcher> matchers) {
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(Paths.get(osPath(field.getQualifiedName()))) || matcher.matches(Paths.get(osPath(field.getFullyQualifiedName())))) {
                return true;
            }
        }
        return false;
    }

    private String osPath(String path) {
        return path.replace('/', File.separatorChar);
    }

    private PathMatcher matcher(String pattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + (pattern.startsWith("/") ? pattern.substring(1) : pattern));
    }

    private List<String> patterns(String first, String[] rest) {
        List<String> patterns = new ArrayList<>();
        patterns.add(assertNotNull(first));
        Collections.addAll(patterns, assertNotNull(rest));
        return patterns;
    }

    @Override
    public boolean contains(String pattern) {
        return !getFields(pattern).isEmpty();
    }

    @Override
    public boolean containsAnyOf(String pattern, String... patterns) {
        requireDocument();
        return patterns(pattern, patterns).stream().anyMatch(this::contains);
    }

    @Override
    public boolean containsAllOf(String pattern, String... patterns) {
        requireDocument();
        return patterns(pattern, patterns).stream().allMatch(this::contains);
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() {
        return groupByResultKey(getFields());
    }

    @Override
    public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String pattern, String... patterns) {
        return groupByResultKey(getFields(pattern, patterns));
    }

    private Map<String, List<SelectedField>> groupByResultKey(List<SelectedField> fields) {
        Map<String, List<SelectedField>> groups = new LinkedHashMap<>();
        fields.forEach(field -> groups.computeIfAbsent(field.getResultKey(), ignored -> new ArrayList<>()).add(field));
        groups.replaceAll((key, value) -> List.copyOf(value));
        return Collections.unmodifiableMap(groups);
    }
}
