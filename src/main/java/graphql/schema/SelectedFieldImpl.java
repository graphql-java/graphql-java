package graphql.schema;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Field;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static graphql.util.FpKit.interThreadMemoize;

@Internal
public final class SelectedFieldImpl implements SelectedField {
    private final ExecutionContext context;
    private final List<Field> fields;
    private final GraphQLObjectType objectType;
    private final GraphQLFieldDefinition definition;
    private final int level;
    private final boolean conditional;
    private final @Nullable SelectedField parent;
    private final String qualifiedName;
    private final String fullyQualifiedName;
    private final Supplier<Map<String, Object>> arguments;
    private final Supplier<DataFetchingFieldSelectionSet> children;
    private final Supplier<DataFetchingFieldSelectionSetImpl> traversalChildren;

    public SelectedFieldImpl(ExecutionContext context, List<Field> fields, GraphQLObjectType objectType, int level,
                             boolean conditional, @Nullable SelectedField parent, String simplePrefix, String typePrefix) {
        this.context = context;
        this.fields = List.copyOf(fields);
        this.objectType = objectType;
        this.definition = Introspection.getFieldDef(context.getGraphQLSchema(), objectType, fields.get(0).getName());
        this.level = level;
        this.conditional = conditional;
        this.parent = parent;
        this.qualifiedName = qualify(simplePrefix, getName());
        this.fullyQualifiedName = qualify(typePrefix, objectType.getName() + "." + getName());
        this.arguments = interThreadMemoize(() -> ValuesResolver.getArgumentValues(context.getGraphQLSchema().getCodeRegistry(),
                definition.getArguments(), fields.get(0).getArguments(), context.getCoercedVariables(), context.getGraphQLContext(), context.getLocale()));
        this.children = interThreadMemoize(() -> childSelectionSet("", ""));
        this.traversalChildren = interThreadMemoize(() -> childSelectionSet(qualifiedName, fullyQualifiedName));
    }

    private String qualify(String prefix, String name) {
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private DataFetchingFieldSelectionSetImpl childSelectionSet(String simplePrefix, String typePrefix) {
        return new DataFetchingFieldSelectionSetImpl(context, fields, getType(), level, this, simplePrefix, typePrefix);
    }

    public List<SelectedField> getTraversalChildren(LookaheadFieldBudget budget) {
        return traversalChildren.get().getImmediateFields(budget);
    }

    @Override
    public String getName() {
        return fields.get(0).getName();
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
    public List<GraphQLObjectType> getObjectTypes() {
        return List.of(objectType);
    }

    @Override
    public List<String> getObjectTypeNames() {
        return List.of(objectType.getName());
    }

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return List.of(definition);
    }

    @Override
    public GraphQLOutputType getType() {
        return definition.getType();
    }

    @Override
    public Map<String, Object> getArguments() {
        return arguments.get();
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public boolean isConditional() {
        return conditional;
    }

    @Override
    public @Nullable String getAlias() {
        return fields.get(0).getAlias();
    }

    @Override
    public String getResultKey() {
        return fields.get(0).getResultKey();
    }

    @Override
    public @Nullable SelectedField getParentField() {
        return parent;
    }

    @Override
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return children.get();
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }
}
