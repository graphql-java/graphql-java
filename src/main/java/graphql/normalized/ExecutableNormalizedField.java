package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.Mutable;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.normalized.incremental.NormalizedDeferredExecution;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.util.FpKit;
import graphql.util.MutableRef;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An {@link ExecutableNormalizedField} represents a field in an executable graphql operation.  Its models what
 * could be executed during a given operation.
 * <p>
 * This class is intentionally mutable for performance reasons since building immutable parent child
 * objects is too expensive.
 */
@PublicApi
@Mutable
public class ExecutableNormalizedField {
    private final String alias;
    private final ImmutableMap<String, NormalizedInputValue> normalizedArguments;
    private final LinkedHashMap<String, Object> resolvedArguments;
    private final ImmutableList<Argument> astArguments;

    // Mutable List on purpose: it is modified after creation
    private final LinkedHashSet<String> objectTypeNames;
    private final ArrayList<ExecutableNormalizedField> children;
    private ExecutableNormalizedField parent;

    private final String fieldName;
    private final int level;

    // Mutable List on purpose: it is modified after creation
    private final LinkedHashSet<NormalizedDeferredExecution> deferredExecutions;

    private ExecutableNormalizedField(Builder builder) {
        this.alias = builder.alias;
        this.resolvedArguments = builder.resolvedArguments;
        this.normalizedArguments = builder.normalizedArguments;
        this.astArguments = builder.astArguments;
        this.objectTypeNames = builder.objectTypeNames;
        this.fieldName = assertNotNull(builder.fieldName);
        this.children = builder.children;
        this.level = builder.level;
        this.parent = builder.parent;
        this.deferredExecutions = builder.deferredExecutions;
    }

    /**
     * Determines whether this {@link ExecutableNormalizedField} needs a fragment to select the field. However, it considers the parent
     * output type when determining whether it needs a fragment.
     * <p>
     * Consider the following schema
     *
     * <pre>
     * interface Animal {
     *     name: String
     *     parent: Animal
     * }
     * type Cat implements Animal {
     *     name: String
     *     parent: Cat
     * }
     * type Dog implements Animal {
     *     name: String
     *     parent: Dog
     *     isGoodBoy: Boolean
     * }
     * type Query {
     *     animal: Animal
     * }
     * </pre>
     * <p>
     * and the following query
     *
     * <pre>
     * {
     *     animal {
     *         parent {
     *             name
     *         }
     *     }
     * }
     * </pre>
     * <p>
     * Then we would get the following {@link ExecutableNormalizedOperation}
     *
     * <pre>
     * -Query.animal: Animal
     * --[Cat, Dog].parent: Cat, Dog
     * ---[Cat, Dog].name: String
     * </pre>
     * <p>
     * If we simply checked the {@link #parent}'s {@link #getFieldDefinitions(GraphQLSchema)} that would
     * point us to {@code Cat.parent} and {@code Dog.parent} whose output types would incorrectly answer
     * our question whether this is conditional?
     * <p>
     * We MUST consider that the output type of the {@code parent} field is {@code Animal} and
     * NOT {@code Cat} or {@code Dog} as their respective implementations would say.
     *
     * @param schema - the graphql schema in play
     *
     * @return true if the field is conditional
     */
    public boolean isConditional(@NonNull GraphQLSchema schema) {
        if (parent == null) {
            return false;
        }

        for (GraphQLInterfaceType commonParentOutputInterface : parent.getInterfacesCommonToAllOutputTypes(schema)) {
            List<GraphQLObjectType> implementations = schema.getImplementations(commonParentOutputInterface);
            // __typename
            if (fieldName.equals(Introspection.TypeNameMetaFieldDef.getName()) && implementations.size() == objectTypeNames.size()) {
                return false;
            }
            if (commonParentOutputInterface.getField(fieldName) == null) {
                continue;
            }
            if (implementations.size() == objectTypeNames.size()) {
                return false;
            }
        }

        // __typename is the only field in a union type that CAN be NOT conditional
        GraphQLFieldDefinition parentFieldDef = parent.getOneFieldDefinition(schema);
        if (unwrapAll(parentFieldDef.getType()) instanceof GraphQLUnionType) {
            GraphQLUnionType parentOutputTypeAsUnion = (GraphQLUnionType) unwrapAll(parentFieldDef.getType());
            if (fieldName.equals(Introspection.TypeNameMetaFieldDef.getName()) && objectTypeNames.size() == parentOutputTypeAsUnion.getTypes().size()) {
                return false; // Not conditional
            }
        }

        // This means there is no Union or Interface which could serve as unconditional parent
        if (objectTypeNames.size() > 1) {
            return true; // Conditional
        }
        if (parent.objectTypeNames.size() > 1) {
            return true;
        }

        GraphQLObjectType oneObjectType = (GraphQLObjectType) schema.getType(objectTypeNames.iterator().next());
        return unwrapAll(parentFieldDef.getType()) != oneObjectType;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public GraphQLOutputType getType(GraphQLSchema schema) {
        List<GraphQLFieldDefinition> fieldDefinitions = getFieldDefinitions(schema);
        Set<String> fieldTypes = fieldDefinitions.stream().map(fd -> simplePrint(fd.getType())).collect(toSet());
        assertTrue(fieldTypes.size() == 1, "More than one type ... use getTypes");
        return fieldDefinitions.get(0).getType();
    }

    public List<GraphQLOutputType> getTypes(GraphQLSchema schema) {
        return ImmutableKit.map(getFieldDefinitions(schema), fd -> fd.getType());
    }

    public void forEachFieldDefinition(GraphQLSchema schema, Consumer<GraphQLFieldDefinition> consumer) {
        var fieldDefinition = resolveIntrospectionField(schema, objectTypeNames, fieldName);
        if (fieldDefinition != null) {
            consumer.accept(fieldDefinition);
            return;
        }

        var fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        for (String objectTypeName : objectTypeNames) {
            GraphQLObjectType type = (GraphQLObjectType) assertNotNull(schema.getType(objectTypeName));
            // Use field visibility to allow custom visibility implementations to provide placeholder fields
            // for fields that don't exist on the local schema (e.g., in federated subgraphs)
            GraphQLFieldDefinition field = fieldVisibility.getFieldDefinition(type, fieldName);
            consumer.accept(assertNotNull(field, "No field %s found for type %s", fieldName, objectTypeName));
        }
    }

    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLSchema schema) {
        ImmutableList.Builder<GraphQLFieldDefinition> builder = ImmutableList.builder();
        forEachFieldDefinition(schema, builder::add);
        return builder.build();
    }

    /**
     * This is NOT public as it is not recommended usage.
     * <p>
     * Internally there are cases where we know it is safe to use this, so this exists.
     */
    private GraphQLFieldDefinition getOneFieldDefinition(GraphQLSchema schema) {
        var fieldDefinition = resolveIntrospectionField(schema, objectTypeNames, fieldName);
        if (fieldDefinition != null) {
            return fieldDefinition;
        }

        String objectTypeName = objectTypeNames.iterator().next();
        GraphQLObjectType type = (GraphQLObjectType) assertNotNull(schema.getType(objectTypeName));
        var fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        return assertNotNull(fieldVisibility.getFieldDefinition(type, fieldName), "No field %s found for type %s", fieldName, objectTypeName);
    }

    private static GraphQLFieldDefinition resolveIntrospectionField(GraphQLSchema schema, Set<String> objectTypeNames, String fieldName) {
        if (fieldName.equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
            return schema.getIntrospectionTypenameFieldDefinition();
        } else if (objectTypeNames.size() == 1 && objectTypeNames.iterator().next().equals(schema.getQueryType().getName())) {
            if (fieldName.equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                return schema.getIntrospectionSchemaFieldDefinition();
            } else if (fieldName.equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                return schema.getIntrospectionTypeFieldDefinition();
            }
        }
        return null;
    }

    @Internal
    public void addObjectTypeNames(Collection<String> objectTypeNames) {
        this.objectTypeNames.addAll(objectTypeNames);
    }

    @Internal
    public void setObjectTypeNames(Collection<String> objectTypeNames) {
        this.objectTypeNames.clear();
        this.objectTypeNames.addAll(objectTypeNames);
    }

    @Internal
    public void addChild(ExecutableNormalizedField executableNormalizedField) {
        this.children.add(executableNormalizedField);
    }

    @Internal
    public void clearChildren() {
        this.children.clear();
    }

    @Internal
    public void setDeferredExecutions(Collection<NormalizedDeferredExecution> deferredExecutions) {
        this.deferredExecutions.clear();
        this.deferredExecutions.addAll(deferredExecutions);
    }

    public void addDeferredExecutions(Collection<NormalizedDeferredExecution> deferredExecutions) {
        this.deferredExecutions.addAll(deferredExecutions);
    }

    /**
     * All merged fields have the same name so this is the name of the {@link ExecutableNormalizedField}.
     * <p>
     * WARNING: This is not always the key in the execution result, because of possible field aliases.
     *
     * @return the name of this {@link ExecutableNormalizedField}
     *
     * @see #getResultKey()
     * @see #getAlias()
     */
    public String getName() {
        return getFieldName();
    }

    /**
     * @return the same value as {@link #getName()}
     *
     * @see #getResultKey()
     * @see #getAlias()
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the result key of this {@link ExecutableNormalizedField} within the overall result.
     * This is either a field alias or the value of {@link #getName()}
     *
     * @return the result key for this {@link ExecutableNormalizedField}.
     *
     * @see #getName()
     */
    public String getResultKey() {
        if (alias != null) {
            return alias;
        }
        return getName();
    }

    /**
     * @return the field alias used or null if there is none
     *
     * @see #getResultKey()
     * @see #getName()
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @return a list of the {@link Argument}s on the field
     */
    public ImmutableList<Argument> getAstArguments() {
        return astArguments;
    }

    /**
     * Returns an argument value as a {@link NormalizedInputValue} which contains its type name and its current value
     *
     * @param name the name of the argument
     *
     * @return an argument value
     */
    public NormalizedInputValue getNormalizedArgument(String name) {
        return normalizedArguments.get(name);
    }

    /**
     * @return a map of all the arguments in {@link NormalizedInputValue} form
     */
    public ImmutableMap<String, NormalizedInputValue> getNormalizedArguments() {
        return normalizedArguments;
    }

    /**
     * @return a map of the resolved argument values
     */
    public LinkedHashMap<String, Object> getResolvedArguments() {
        return resolvedArguments;
    }


    /**
     * A {@link ExecutableNormalizedField} can sometimes (for non-concrete types like interfaces and unions)
     * have more than one object type it could be when executed.  There is no way to know what it will be until
     * the field is executed over data and the type is resolved via a {@link graphql.schema.TypeResolver}.
     * <p>
     * This method returns all the possible types a field can be which is one or more {@link GraphQLObjectType}
     * names.
     * <p>
     * Warning: This returns a Mutable Set. No defensive copy is made for performance reasons.
     *
     * @return a set of the possible type names this field could be.
     */
    public Set<String> getObjectTypeNames() {
        return objectTypeNames;
    }


    /**
     * This returns the first entry in {@link #getObjectTypeNames()}.  Sometimes you know a field cant be more than one
     * type and this method is a shortcut one to help you.
     *
     * @return the first entry from
     */
    public String getSingleObjectTypeName() {
        return objectTypeNames.iterator().next();
    }

    /**
     * @return a helper method show field details
     */
    public String printDetails() {
        StringBuilder result = new StringBuilder();
        if (getAlias() != null) {
            result.append(getAlias()).append(": ");
        }
        return result + objectTypeNamesToString() + "." + fieldName;
    }

    /**
     * @return a helper method to show the object types names as a string
     */
    public String objectTypeNamesToString() {
        if (objectTypeNames.size() == 1) {
            return objectTypeNames.iterator().next();
        } else {
            return objectTypeNames.toString();
        }
    }

    /**
     * This returns the list of the result keys (see {@link #getResultKey()} that lead from this field upwards to
     * its parent field
     *
     * @return a list of the result keys from this {@link ExecutableNormalizedField} to the top of the operation via parent fields
     */
    public List<String> getListOfResultKeys() {
        LinkedList<String> list = new LinkedList<>();
        ExecutableNormalizedField current = this;
        while (current != null) {
            list.addFirst(current.getResultKey());
            current = current.parent;
        }
        return list;
    }

    /**
     * @return the children of the {@link ExecutableNormalizedField}
     */
    public List<ExecutableNormalizedField> getChildren() {
        return children;
    }

    /**
     * Returns the list of child fields that would have the same result key
     *
     * @param resultKey the result key to check
     *
     * @return a list of all direct {@link ExecutableNormalizedField} children with the specified result key
     */
    public List<ExecutableNormalizedField> getChildrenWithSameResultKey(String resultKey) {
        return FpKit.filterList(children, child -> child.getResultKey().equals(resultKey));
    }

    public List<ExecutableNormalizedField> getChildren(int includingRelativeLevel) {
        assertTrue(includingRelativeLevel >= 1, "relative level must be >= 1");
        List<ExecutableNormalizedField> result = new ArrayList<>();

        this.getChildren().forEach(child -> {
            traverseImpl(child, result::add, 1, includingRelativeLevel);
        });
        return result;
    }

    /**
     * This returns the child fields that can be used if the object is of the specified object type
     *
     * @param objectTypeName the object type
     *
     * @return a list of child fields that would apply to that object type
     */
    public List<ExecutableNormalizedField> getChildren(String objectTypeName) {
        return children.stream()
                .filter(cld -> cld.objectTypeNames.contains(objectTypeName))
                .collect(toList());
    }

    /**
     * the level of the {@link ExecutableNormalizedField} in the operation hierarchy with top level fields
     * starting at 1
     *
     * @return the level of the {@link ExecutableNormalizedField} in the operation hierarchy
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return the parent of this {@link ExecutableNormalizedField} or null if it's a top level field
     */
    public ExecutableNormalizedField getParent() {
        return parent;
    }

    /**
     * @return the {@link NormalizedDeferredExecution}s associated with this {@link ExecutableNormalizedField}.
     *
     * @see NormalizedDeferredExecution
     */
    @ExperimentalApi
    public LinkedHashSet<NormalizedDeferredExecution> getDeferredExecutions() {
        return deferredExecutions;
    }

    @Internal
    public void replaceParent(ExecutableNormalizedField newParent) {
        this.parent = newParent;
    }


    @Override
    public String toString() {
        return "NormalizedField{" +
                objectTypeNamesToString() + "." + fieldName +
                ", alias=" + alias +
                ", level=" + level +
                ", children=" + children.stream().map(ExecutableNormalizedField::toString).collect(joining("\n")) +
                '}';
    }


    /**
     * Traverse from this {@link ExecutableNormalizedField} down into itself and all of its children
     *
     * @param consumer the callback for each {@link ExecutableNormalizedField} in the hierarchy.
     */
    public void traverseSubTree(Consumer<ExecutableNormalizedField> consumer) {
        this.getChildren().forEach(child -> {
            traverseImpl(child, consumer, 1, Integer.MAX_VALUE);
        });
    }

    private void traverseImpl(ExecutableNormalizedField root,
                              Consumer<ExecutableNormalizedField> consumer,
                              int curRelativeLevel,
                              int abortAfter) {
        if (curRelativeLevel > abortAfter) {
            return;
        }
        consumer.accept(root);
        root.getChildren().forEach(child -> {
            traverseImpl(child, consumer, curRelativeLevel + 1, abortAfter);
        });
    }

    /**
     * This tries to find interfaces common to all the field output types.
     * <p>
     * i.e. goes through {@link #getFieldDefinitions(GraphQLSchema)} and finds interfaces that
     * all the field's unwrapped output types are assignable to.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<GraphQLInterfaceType> getInterfacesCommonToAllOutputTypes(GraphQLSchema schema) {
        // Shortcut for performance
        if (objectTypeNames.size() == 1) {
            var fieldDef = getOneFieldDefinition(schema);
            var outputType = unwrapAll(fieldDef.getType());

            if (outputType instanceof GraphQLObjectType) {
                return new LinkedHashSet<>((List) ((GraphQLObjectType) outputType).getInterfaces());
            } else if (outputType instanceof GraphQLInterfaceType) {
                var result = new LinkedHashSet<>((List) ((GraphQLInterfaceType) outputType).getInterfaces());
                result.add(outputType);
                return result;
            } else {
                return Collections.emptySet();
            }
        }

        MutableRef<Set<GraphQLInterfaceType>> commonInterfaces = new MutableRef<>();
        forEachFieldDefinition(schema, (fieldDef) -> {
            var outputType = unwrapAll(fieldDef.getType());

            List<GraphQLInterfaceType> outputTypeInterfaces;
            if (outputType instanceof GraphQLObjectType) {
                outputTypeInterfaces = (List) ((GraphQLObjectType) outputType).getInterfaces();
            } else if (outputType instanceof GraphQLInterfaceType) {
                // This interface and superinterfaces
                List<GraphQLNamedOutputType> superInterfaces = ((GraphQLInterfaceType) outputType).getInterfaces();

                outputTypeInterfaces = new ArrayList<>(superInterfaces.size() + 1);
                outputTypeInterfaces.add((GraphQLInterfaceType) outputType);

                if (!superInterfaces.isEmpty()) {
                    outputTypeInterfaces.addAll((List) superInterfaces);
                }
            } else {
                outputTypeInterfaces = Collections.emptyList();
            }

            if (commonInterfaces.value == null) {
                commonInterfaces.value = new LinkedHashSet<>(outputTypeInterfaces);
            } else {
                commonInterfaces.value.retainAll(outputTypeInterfaces);
            }
        });

        return commonInterfaces.value;
    }

    /**
     * @return a {@link Builder} of {@link ExecutableNormalizedField}s
     */
    public static Builder newNormalizedField() {
        return new Builder();
    }

    /**
     * Allows this {@link ExecutableNormalizedField} to be transformed via a {@link Builder} consumer callback
     *
     * @param builderConsumer the consumer given a builder
     *
     * @return a new transformed {@link ExecutableNormalizedField}
     */
    public ExecutableNormalizedField transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static class Builder {
        private LinkedHashSet<String> objectTypeNames = new LinkedHashSet<>();
        private String fieldName;
        private ArrayList<ExecutableNormalizedField> children = new ArrayList<>();
        private int level;
        private ExecutableNormalizedField parent;
        private String alias;
        private ImmutableMap<String, NormalizedInputValue> normalizedArguments = ImmutableKit.emptyMap();
        private LinkedHashMap<String, Object> resolvedArguments = new LinkedHashMap<>();
        private ImmutableList<Argument> astArguments = ImmutableKit.emptyList();

        private LinkedHashSet<NormalizedDeferredExecution> deferredExecutions = new LinkedHashSet<>();

        private Builder() {
        }

        private Builder(ExecutableNormalizedField existing) {
            this.alias = existing.alias;
            this.normalizedArguments = existing.normalizedArguments;
            this.astArguments = existing.astArguments;
            this.resolvedArguments = existing.resolvedArguments;
            this.objectTypeNames = new LinkedHashSet<>(existing.getObjectTypeNames());
            this.fieldName = existing.getFieldName();
            this.children = new ArrayList<>(existing.children);
            this.level = existing.getLevel();
            this.parent = existing.getParent();
            this.deferredExecutions = existing.getDeferredExecutions();
        }

        public Builder clearObjectTypesNames() {
            this.objectTypeNames.clear();
            return this;
        }

        public Builder objectTypeNames(List<String> objectTypeNames) {
            this.objectTypeNames.addAll(objectTypeNames);
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder normalizedArguments(@Nullable Map<String, NormalizedInputValue> arguments) {
            this.normalizedArguments = arguments == null ? ImmutableKit.emptyMap() : ImmutableMap.copyOf(arguments);
            return this;
        }

        public Builder resolvedArguments(@Nullable Map<String, Object> arguments) {
            this.resolvedArguments = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
            return this;
        }

        public Builder astArguments(@NonNull List<Argument> astArguments) {
            this.astArguments = ImmutableList.copyOf(astArguments);
            return this;
        }


        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }


        public Builder children(List<ExecutableNormalizedField> children) {
            this.children.clear();
            this.children.addAll(children);
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder parent(ExecutableNormalizedField parent) {
            this.parent = parent;
            return this;
        }

        public Builder deferredExecutions(LinkedHashSet<NormalizedDeferredExecution> deferredExecutions) {
            this.deferredExecutions = deferredExecutions;
            return this;
        }

        public ExecutableNormalizedField build() {
            return new ExecutableNormalizedField(this);
        }
    }
}
