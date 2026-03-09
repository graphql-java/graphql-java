package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import graphql.Internal;
import graphql.execution.AbortExecutionException;
import graphql.introspection.Introspection;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.VariableReference;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.impl.SchemaUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.FpKit.filterSet;
import static graphql.util.FpKit.groupingBy;
import static graphql.util.FpKit.intersection;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * Creates a {@link CachedNormalizedOperation} from a GraphQL document and schema.
 * <p>
 * This factory performs the same normalization as {@link ExecutableNormalizedOperationFactory}
 * but WITHOUT evaluating skip/include directives or coercing argument values. Instead,
 * skip/include conditions are captured as {@link FieldInclusionCondition}s on each field.
 * <p>
 * The result is a variable-independent tree that can be cached and later materialized
 * into an {@link ExecutableNormalizedOperation} with concrete variable values.
 */
@Internal
public class CachedNormalizedOperationFactory {

    private static final int DEFAULT_MAX_FIELDS_COUNT = 100_000;

    public static CachedNormalizedOperation createCachedOperation(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName
    ) {
        return createCachedOperation(graphQLSchema, document, operationName, Integer.MAX_VALUE, DEFAULT_MAX_FIELDS_COUNT);
    }

    public static CachedNormalizedOperation createCachedOperation(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName,
            int maxChildrenDepth,
            int maxFieldsCount
    ) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new Impl(
                graphQLSchema,
                getOperationResult.operationDefinition,
                getOperationResult.fragmentsByName,
                maxChildrenDepth,
                maxFieldsCount
        ).build();
    }

    private static class Impl {
        private final GraphQLSchema graphQLSchema;
        private final OperationDefinition operationDefinition;
        private final Map<String, FragmentDefinition> fragments;
        private final int maxChildrenDepth;
        private final int maxFieldsCount;

        private int fieldCount = 0;
        private int maxDepthSeen = 0;
        private final List<CachedNormalizedField> rootFields = new ArrayList<>();

        Impl(GraphQLSchema graphQLSchema,
             OperationDefinition operationDefinition,
             Map<String, FragmentDefinition> fragments,
             int maxChildrenDepth,
             int maxFieldsCount) {
            this.graphQLSchema = graphQLSchema;
            this.operationDefinition = operationDefinition;
            this.fragments = fragments;
            this.maxChildrenDepth = maxChildrenDepth;
            this.maxFieldsCount = maxFieldsCount;
        }

        CachedNormalizedOperation build() {
            buildRecursively(null, null, 0);
            return new CachedNormalizedOperation(
                    operationDefinition.getOperation(),
                    operationDefinition.getName(),
                    rootFields,
                    fieldCount,
                    maxDepthSeen
            );
        }

        private void buildRecursively(CachedNormalizedField parent,
                                      ImmutableList<CollectedField> parentCollectedFields,
                                      int curLevel) {
            if (maxDepthSeen < curLevel) {
                maxDepthSeen = curLevel;
                if (curLevel > maxChildrenDepth) {
                    throw new AbortExecutionException("Maximum query depth exceeded. " + curLevel + " > " + maxChildrenDepth);
                }
            }

            Set<GraphQLObjectType> possibleObjects;
            List<CollectedField> collectedFields;

            if (parent == null) {
                // Root level
                GraphQLObjectType rootType = SchemaUtil.getOperationRootType(graphQLSchema, operationDefinition);
                possibleObjects = ImmutableSet.of(rootType);
                collectedFields = new ArrayList<>();
                collectFromSelectionSet(operationDefinition.getSelectionSet(), collectedFields, rootType, possibleObjects, FieldInclusionCondition.ALWAYS);
            } else {
                List<GraphQLFieldDefinition> fieldDefs = getFieldDefinitions(parent);
                possibleObjects = resolvePossibleObjects(fieldDefs);
                if (possibleObjects.isEmpty()) {
                    return;
                }
                collectedFields = new ArrayList<>();
                for (CollectedField cf : parentCollectedFields) {
                    if (cf.field.getSelectionSet() == null) {
                        continue;
                    }
                    GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(graphQLSchema, cf.astTypeCondition, cf.field.getName());
                    GraphQLCompositeType selectionSetType = (GraphQLCompositeType) unwrapAll(fieldDefinition.getType());
                    collectFromSelectionSet(cf.field.getSelectionSet(), collectedFields, selectionSetType, possibleObjects, cf.inclusionCondition);
                }
            }

            Map<String, List<CollectedField>> fieldsByResultKey = fieldsByResultKey(collectedFields);
            for (Map.Entry<String, List<CollectedField>> entry : fieldsByResultKey.entrySet()) {
                List<CollectedFieldGroup> groups = groupByCommonParents(entry.getValue());
                for (CollectedFieldGroup group : groups) {
                    CachedNormalizedField cnf = createCNF(group, curLevel + 1, parent);
                    if (cnf == null) {
                        continue;
                    }

                    ImmutableList<CollectedField> groupFields = ImmutableList.copyOf(group.fields);

                    if (parent == null) {
                        rootFields.add(cnf);
                    } else {
                        parent.addChild(cnf);
                    }

                    buildRecursively(cnf, groupFields, curLevel + 1);
                }
            }
        }

        private CachedNormalizedField createCNF(CollectedFieldGroup group, int level, CachedNormalizedField parent) {
            fieldCount++;
            if (fieldCount > maxFieldsCount) {
                throw new AbortExecutionException("Maximum field count exceeded. " + fieldCount + " > " + maxFieldsCount);
            }

            Field field = group.fields.iterator().next().field;
            Set<GraphQLObjectType> objectTypes = group.objectTypes;

            // Combine inclusion conditions from all collected fields in the group.
            // If all have the same condition (common case), use that. Otherwise,
            // since these fields share the same result key and type group, a field is
            // included if ANY of its collected sources is included (OR semantics within a group).
            // For the prototype we take the first field's condition as representative,
            // since within a CollectedFieldGroup all fields share the same astTypeCondition
            // and thus the same inclusion condition path.
            FieldInclusionCondition condition = group.fields.iterator().next().inclusionCondition;

            ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
            return CachedNormalizedField.newCachedField()
                    .fieldName(field.getName())
                    .alias(field.getAlias())
                    .astArguments(field.getArguments())
                    .objectTypeNames(new LinkedHashSet<>(objectTypeNames))
                    .level(level)
                    .parent(parent)
                    .inclusionCondition(condition)
                    .build();
        }

        // --- Field collection (mirrors ExecutableNormalizedOperationFactory but captures conditions) ---

        private void collectFromSelectionSet(SelectionSet selectionSet,
                                             List<CollectedField> result,
                                             GraphQLCompositeType astTypeCondition,
                                             Set<GraphQLObjectType> possibleObjects,
                                             FieldInclusionCondition inheritedCondition) {
            for (Selection<?> selection : selectionSet.getSelections()) {
                if (selection instanceof Field) {
                    collectField(result, (Field) selection, possibleObjects, astTypeCondition, inheritedCondition);
                } else if (selection instanceof InlineFragment) {
                    collectInlineFragment(result, (InlineFragment) selection, possibleObjects, astTypeCondition, inheritedCondition);
                } else if (selection instanceof FragmentSpread) {
                    collectFragmentSpread(result, (FragmentSpread) selection, possibleObjects, inheritedCondition);
                }
            }
        }

        private void collectField(List<CollectedField> result,
                                  Field field,
                                  Set<GraphQLObjectType> possibleObjectTypes,
                                  GraphQLCompositeType astTypeCondition,
                                  FieldInclusionCondition inheritedCondition) {
            if (possibleObjectTypes.isEmpty()) {
                return;
            }

            FieldInclusionCondition fieldCondition = extractCondition(field);
            // If the field has a literal condition that resolves to NEVER, skip it entirely
            if (fieldCondition == FieldInclusionCondition.NEVER) {
                return;
            }
            FieldInclusionCondition combinedCondition = inheritedCondition.and(fieldCondition);
            result.add(new CollectedField(field, possibleObjectTypes, astTypeCondition, combinedCondition));
        }

        private void collectInlineFragment(List<CollectedField> result,
                                           InlineFragment inlineFragment,
                                           Set<GraphQLObjectType> possibleObjects,
                                           GraphQLCompositeType astTypeCondition,
                                           FieldInclusionCondition inheritedCondition) {
            FieldInclusionCondition fragmentCondition = extractCondition(inlineFragment);
            if (fragmentCondition == FieldInclusionCondition.NEVER) {
                return;
            }
            FieldInclusionCondition combinedCondition = inheritedCondition.and(fragmentCondition);

            Set<GraphQLObjectType> newPossibleObjects = possibleObjects;
            GraphQLCompositeType newAstTypeCondition = astTypeCondition;

            if (inlineFragment.getTypeCondition() != null) {
                newAstTypeCondition = (GraphQLCompositeType) graphQLSchema.getType(inlineFragment.getTypeCondition().getName());
                newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);
            }

            collectFromSelectionSet(inlineFragment.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects, combinedCondition);
        }

        private void collectFragmentSpread(List<CollectedField> result,
                                           FragmentSpread fragmentSpread,
                                           Set<GraphQLObjectType> possibleObjects,
                                           FieldInclusionCondition inheritedCondition) {
            FieldInclusionCondition spreadCondition = extractCondition(fragmentSpread);
            if (spreadCondition == FieldInclusionCondition.NEVER) {
                return;
            }
            FieldInclusionCondition combinedCondition = inheritedCondition.and(spreadCondition);

            FragmentDefinition fragmentDefinition = assertNotNull(fragments.get(fragmentSpread.getName()));

            FieldInclusionCondition defCondition = extractCondition(fragmentDefinition);
            if (defCondition == FieldInclusionCondition.NEVER) {
                return;
            }
            combinedCondition = combinedCondition.and(defCondition);

            GraphQLCompositeType newAstTypeCondition = (GraphQLCompositeType) assertNotNull(
                    graphQLSchema.getType(fragmentDefinition.getTypeCondition().getName()));
            Set<GraphQLObjectType> newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);

            collectFromSelectionSet(fragmentDefinition.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects, combinedCondition);
        }

        /**
         * Extracts a FieldInclusionCondition from the skip/include directives on an AST node.
         * Literal boolean values are resolved immediately; variable references are kept as deferred conditions.
         */
        private FieldInclusionCondition extractCondition(graphql.language.DirectivesContainer<?> node) {
            FieldInclusionCondition condition = FieldInclusionCondition.ALWAYS;

            Directive skipDirective = findDirective(node.getDirectives(), SkipDirective.getName());
            if (skipDirective != null) {
                FieldInclusionCondition skipCondition = directiveArgToCondition(skipDirective, true);
                condition = condition.and(skipCondition);
            }

            Directive includeDirective = findDirective(node.getDirectives(), IncludeDirective.getName());
            if (includeDirective != null) {
                FieldInclusionCondition includeCondition = directiveArgToCondition(includeDirective, false);
                condition = condition.and(includeCondition);
            }

            return condition;
        }

        private FieldInclusionCondition directiveArgToCondition(Directive directive, boolean isSkip) {
            var argument = directive.getArgument("if");
            if (argument == null) {
                return FieldInclusionCondition.ALWAYS;
            }
            var value = argument.getValue();
            if (value instanceof BooleanValue) {
                boolean boolVal = ((BooleanValue) value).isValue();
                if (isSkip) {
                    // @skip(if: true) → NEVER, @skip(if: false) → ALWAYS
                    return boolVal ? FieldInclusionCondition.NEVER : FieldInclusionCondition.ALWAYS;
                } else {
                    // @include(if: true) → ALWAYS, @include(if: false) → NEVER
                    return boolVal ? FieldInclusionCondition.ALWAYS : FieldInclusionCondition.NEVER;
                }
            }
            if (value instanceof VariableReference) {
                String varName = ((VariableReference) value).getName();
                if (isSkip) {
                    return FieldInclusionCondition.skipIf(varName);
                } else {
                    return FieldInclusionCondition.includeIf(varName);
                }
            }
            return FieldInclusionCondition.ALWAYS;
        }

        private static Directive findDirective(List<Directive> directives, String name) {
            for (Directive directive : directives) {
                if (directive.getName().equals(name)) {
                    return directive;
                }
            }
            return null;
        }

        // --- Grouping logic (same as ExecutableNormalizedOperationFactory) ---

        private Map<String, List<CollectedField>> fieldsByResultKey(List<CollectedField> collectedFields) {
            Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
            for (CollectedField cf : collectedFields) {
                fieldsByName.computeIfAbsent(cf.field.getResultKey(), k -> new ArrayList<>()).add(cf);
            }
            return fieldsByName;
        }

        private List<CollectedFieldGroup> groupByCommonParents(Collection<CollectedField> fields) {
            ImmutableSet.Builder<GraphQLObjectType> objectTypes = ImmutableSet.builder();
            for (CollectedField cf : fields) {
                objectTypes.addAll(cf.objectTypes);
            }
            Set<GraphQLObjectType> allRelevantObjects = objectTypes.build();
            Map<GraphQLType, ImmutableList<CollectedField>> groupByAstParent = groupingBy(fields, f -> f.astTypeCondition);
            if (groupByAstParent.size() == 1) {
                return singletonList(new CollectedFieldGroup(ImmutableSet.copyOf(fields), allRelevantObjects));
            }
            ImmutableList.Builder<CollectedFieldGroup> result = ImmutableList.builder();
            for (GraphQLObjectType objectType : allRelevantObjects) {
                Set<CollectedField> relevantFields = filterSet(fields, field -> field.objectTypes.contains(objectType));
                result.add(new CollectedFieldGroup(relevantFields, singleton(objectType)));
            }
            return result.build();
        }

        // --- Schema helpers ---

        private List<GraphQLFieldDefinition> getFieldDefinitions(CachedNormalizedField field) {
            ImmutableList.Builder<GraphQLFieldDefinition> builder = ImmutableList.builder();
            for (String objectTypeName : field.getObjectTypeNames()) {
                GraphQLObjectType type = (GraphQLObjectType) assertNotNull(graphQLSchema.getType(objectTypeName));
                GraphQLFieldDefinition fd = Introspection.getFieldDefinition(graphQLSchema, type, field.getFieldName());
                builder.add(fd);
            }
            return builder.build();
        }

        private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                                  GraphQLCompositeType typeCondition) {
            ImmutableSet<GraphQLObjectType> resolved = resolvePossibleObjects(typeCondition);
            if (currentOnes.isEmpty()) {
                return resolved;
            }
            return intersection(currentOnes, resolved);
        }

        private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(List<GraphQLFieldDefinition> defs) {
            ImmutableSet.Builder<GraphQLObjectType> builder = ImmutableSet.builder();
            for (GraphQLFieldDefinition def : defs) {
                GraphQLUnmodifiedType outputType = unwrapAll(def.getType());
                if (outputType instanceof GraphQLCompositeType) {
                    builder.addAll(resolvePossibleObjects((GraphQLCompositeType) outputType));
                }
            }
            return builder.build();
        }

        private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type) {
            if (type instanceof GraphQLObjectType) {
                return ImmutableSet.of((GraphQLObjectType) type);
            } else if (type instanceof GraphQLInterfaceType) {
                return ImmutableSet.copyOf(graphQLSchema.getImplementations((GraphQLInterfaceType) type));
            } else if (type instanceof GraphQLUnionType) {
                List<GraphQLNamedOutputType> unionTypes = ((GraphQLUnionType) type).getTypes();
                return ImmutableSet.copyOf(map(unionTypes, GraphQLObjectType.class::cast));
            } else {
                return assertShouldNeverHappen();
            }
        }

        // --- Inner data classes ---

        private static class CollectedField {
            final Field field;
            final Set<GraphQLObjectType> objectTypes;
            final GraphQLCompositeType astTypeCondition;
            final FieldInclusionCondition inclusionCondition;

            CollectedField(Field field, Set<GraphQLObjectType> objectTypes,
                          GraphQLCompositeType astTypeCondition, FieldInclusionCondition inclusionCondition) {
                this.field = field;
                this.objectTypes = objectTypes;
                this.astTypeCondition = astTypeCondition;
                this.inclusionCondition = inclusionCondition;
            }
        }

        private static class CollectedFieldGroup {
            final Set<CollectedField> fields;
            final Set<GraphQLObjectType> objectTypes;

            CollectedFieldGroup(Set<CollectedField> fields, Set<GraphQLObjectType> objectTypes) {
                this.fields = fields;
                this.objectTypes = objectTypes;
            }
        }
    }
}
