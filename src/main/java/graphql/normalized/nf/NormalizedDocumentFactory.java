package graphql.normalized.nf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.AbortExecutionException;
import graphql.execution.CoercedVariables;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.conditional.ConditionalNodes;
import graphql.execution.directives.QueryDirectives;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.execution.incremental.IncrementalUtils;
import graphql.introspection.Introspection;
import graphql.language.*;
import graphql.normalized.ENFMerger;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.NormalizedInputValue;
import graphql.normalized.incremental.NormalizedDeferredExecution;
import graphql.schema.*;
import graphql.schema.impl.SchemaUtil;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static graphql.Assert.*;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.FpKit.*;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

@PublicApi
public class NormalizedDocumentFactory {

    private static final ConditionalNodes conditionalNodes = new ConditionalNodes();

    private NormalizedDocumentFactory() {

    }

    /**
     * This will create a runtime representation of the graphql operation that would be executed
     * in a runtime sense.
     *
     * @param graphQLSchema         the schema to be used
     * @param document              the {@link Document} holding the operation text
     * @param operationName         the operation name to use
     * @param coercedVariableValues the coerced variables to use
     * @return a runtime representation of the graphql operation.
     */
    public static ExecutableNormalizedOperation createExecutableNormalizedOperation(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName,
            CoercedVariables coercedVariableValues
    ) {
        return createExecutableNormalizedOperation(
                graphQLSchema,
                document,
                operationName,
                coercedVariableValues,
                Options.defaultOptions());
    }

    public static NormalizedDocument createNormalizedDocument(
            GraphQLSchema graphQLSchema,
            Document document
    ) {
        return new NormalizedDocumentFactoryImpl(
                graphQLSchema,
                document
        ).createNormalizedDocument();
    }


    private static class NormalizedDocumentFactoryImpl {
        private final GraphQLSchema graphQLSchema;
        private final Document document;

        private final List<PossibleMerger> possibleMergerList = new ArrayList<>();

        private final ImmutableListMultimap.Builder<Field, ExecutableNormalizedField> fieldToNormalizedField = ImmutableListMultimap.builder();
        private final ImmutableMap.Builder<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField = ImmutableMap.builder();
        private final ImmutableMap.Builder<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives = ImmutableMap.builder();
        private final ImmutableListMultimap.Builder<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields = ImmutableListMultimap.builder();
        private int fieldCount = 0;
        private int maxDepthSeen = 0;

        private NormalizedDocumentFactoryImpl(
                GraphQLSchema graphQLSchema,
                Document document
        ) {
            this.graphQLSchema = graphQLSchema;
            this.document = document;
        }

        /**
         * Creates a new ExecutableNormalizedOperation for the provided query
         */
        private NormalizedDocument createNormalizedDocument() {

            List<Definition> operationDefinitions = document.getDefinitions();
            for (Definition definition : operationDefinitions) {
                assertTrue(definition instanceof OperationDefinition, "Only operation definitions expected");
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                GraphQLObjectType rootType = SchemaUtil.getOperationRootType(graphQLSchema, operationDefinition);

                operationDefinition.getVariableDefinitions();
                CollectNFResult collectFromOperationResult = collectFromOperation(rootType, operationDefinition);

                for (ExecutableNormalizedField topLevel : collectFromOperationResult.children) {
                    ImmutableList<FieldAndAstParent> fieldAndAstParents = collectFromOperationResult.normalizedFieldToAstFields.get(topLevel);
                    MergedField mergedField = newMergedField(fieldAndAstParents);

                    captureMergedField(topLevel, mergedField);

                    updateFieldToNFMap(topLevel, fieldAndAstParents);
                    updateCoordinatedToNFMap(topLevel);

                    int depthSeen = buildFieldWithChildren(
                            topLevel,
                            fieldAndAstParents,
                            1);
                    maxDepthSeen = Math.max(maxDepthSeen, depthSeen);
                }
                // getPossibleMergerList
                for (PossibleMerger possibleMerger : possibleMergerList) {
                    List<ExecutableNormalizedField> childrenWithSameResultKey = possibleMerger.parent.getChildrenWithSameResultKey(possibleMerger.resultKey);
                    ENFMerger.merge(possibleMerger.parent, childrenWithSameResultKey, graphQLSchema, options.deferSupport);
                }
            }
            return new ExecutableNormalizedOperation(
                    operationDefinition.getOperation(),
                    operationDefinition.getName(),
                    new ArrayList<>(collectFromOperationResult.children),
                    fieldToNormalizedField.build(),
                    normalizedFieldToMergedField.build(),
                    normalizedFieldToQueryDirectives.build(),
                    coordinatesToNormalizedFields.build(),
                    fieldCount,
                    maxDepthSeen
            );
        }

        private void captureMergedField(ExecutableNormalizedField enf, MergedField mergedFld) {
            // QueryDirectivesImpl is a lazy object and only computes itself when asked for
            QueryDirectives queryDirectives = new QueryDirectivesImpl(mergedFld, graphQLSchema, coercedVariableValues.toMap(), options.getGraphQLContext(), options.getLocale());
            normalizedFieldToQueryDirectives.put(enf, queryDirectives);
            normalizedFieldToMergedField.put(enf, mergedFld);
        }

        private int buildFieldWithChildren(ExecutableNormalizedField executableNormalizedField,
                                           ImmutableList<FieldAndAstParent> fieldAndAstParents,
                                           int curLevel) {
            checkMaxDepthExceeded(curLevel);

            CollectNFResult nextLevel = collectFromMergedField(executableNormalizedField, fieldAndAstParents, curLevel + 1);

            int maxDepthSeen = curLevel;
            for (ExecutableNormalizedField childENF : nextLevel.children) {
                executableNormalizedField.addChild(childENF);
                ImmutableList<FieldAndAstParent> childFieldAndAstParents = nextLevel.normalizedFieldToAstFields.get(childENF);

                MergedField mergedField = newMergedField(childFieldAndAstParents);
                captureMergedField(childENF, mergedField);

                updateFieldToNFMap(childENF, childFieldAndAstParents);
                updateCoordinatedToNFMap(childENF);

                int depthSeen = buildFieldWithChildren(childENF,
                        childFieldAndAstParents,
                        curLevel + 1);
                maxDepthSeen = Math.max(maxDepthSeen, depthSeen);

                checkMaxDepthExceeded(maxDepthSeen);
            }
            return maxDepthSeen;
        }

        private void checkMaxDepthExceeded(int depthSeen) {
            if (depthSeen > this.options.getMaxChildrenDepth()) {
                throw new AbortExecutionException("Maximum query depth exceeded. " + depthSeen + " > " + this.options.getMaxChildrenDepth());
            }
        }

        private static MergedField newMergedField(ImmutableList<FieldAndAstParent> fieldAndAstParents) {
            return MergedField.newMergedField(map(fieldAndAstParents, fieldAndAstParent -> fieldAndAstParent.field)).build();
        }

        private void updateFieldToNFMap(ExecutableNormalizedField executableNormalizedField,
                                        ImmutableList<FieldAndAstParent> mergedField) {
            for (FieldAndAstParent astField : mergedField) {
                fieldToNormalizedField.put(astField.field, executableNormalizedField);
            }
        }

        private void updateCoordinatedToNFMap(ExecutableNormalizedField topLevel) {
            for (String objectType : topLevel.getObjectTypeNames()) {
                FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, topLevel.getFieldName());
                coordinatesToNormalizedFields.put(coordinates, topLevel);
            }
        }

        public CollectNFResult collectFromMergedField(ExecutableNormalizedField executableNormalizedField,
                                                      ImmutableList<FieldAndAstParent> mergedField,
                                                      int level) {
            List<GraphQLFieldDefinition> fieldDefs = executableNormalizedField.getFieldDefinitions(graphQLSchema);
            Set<GraphQLObjectType> possibleObjects = resolvePossibleObjects(fieldDefs);
            if (possibleObjects.isEmpty()) {
                return new CollectNFResult(ImmutableKit.emptyList(), ImmutableListMultimap.of());
            }

            List<CollectedField> collectedFields = new ArrayList<>();
            for (FieldAndAstParent fieldAndAstParent : mergedField) {
                if (fieldAndAstParent.field.getSelectionSet() == null) {
                    continue;
                }
                GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(graphQLSchema, fieldAndAstParent.astParentType, fieldAndAstParent.field.getName());
                GraphQLUnmodifiedType astParentType = unwrapAll(fieldDefinition.getType());
                this.collectFromSelectionSet(fieldAndAstParent.field.getSelectionSet(),
                        collectedFields,
                        (GraphQLCompositeType) astParentType,
                        possibleObjects,
                        null
                );
            }
            Map<String, List<CollectedField>> fieldsByName = fieldsByResultKey(collectedFields);
            ImmutableList.Builder<ExecutableNormalizedField> resultNFs = ImmutableList.builder();
            ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields = ImmutableListMultimap.builder();

            createNFs(resultNFs, fieldsByName, normalizedFieldToAstFields, level, executableNormalizedField);

            return new CollectNFResult(resultNFs.build(), normalizedFieldToAstFields.build());
        }

        private Map<String, List<CollectedField>> fieldsByResultKey(List<CollectedField> collectedFields) {
            Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
            for (CollectedField collectedField : collectedFields) {
                fieldsByName.computeIfAbsent(collectedField.field.getResultKey(), ignored -> new ArrayList<>()).add(collectedField);
            }
            return fieldsByName;
        }

        public CollectNFResult collectFromOperation(GraphQLObjectType rootType, OperationDefinition operationDefinition) {

            Set<GraphQLObjectType> possibleObjects = ImmutableSet.of(rootType);
            List<CollectedField> collectedFields = new ArrayList<>();
            collectFromSelectionSet(operationDefinition.getSelectionSet(), collectedFields, rootType, possibleObjects, null);
            // group by result key
            Map<String, List<CollectedField>> fieldsByName = fieldsByResultKey(collectedFields);
            ImmutableList.Builder<NormalizedField> resultNFs = ImmutableList.builder();

            createNFs(resultNFs, fieldsByName, normalizedFieldToAstFields, 1, null);

            return new CollectNFResult(resultNFs.build()));
        }

        //
        private void createNFs(ImmutableList.Builder<NormalizedField> nfListBuilder,
                               Map<String, List<CollectedField>> fieldsByName,
                               int level,
                               ExecutableNormalizedField parent) {
            for (String resultKey : fieldsByName.keySet()) {
                List<CollectedField> fieldsWithSameResultKey = fieldsByName.get(resultKey);
                List<CollectedFieldGroup> commonParentsGroups = groupByCommonParents(fieldsWithSameResultKey);
                for (CollectedFieldGroup fieldGroup : commonParentsGroups) {
                    ExecutableNormalizedField nf = createNF(fieldGroup, level, parent);
                    if (nf == null) {
                        continue;
                    }
                    for (CollectedField collectedField : fieldGroup.fields) {
                        normalizedFieldToAstFields.put(nf, new FieldAndAstParent(collectedField.field, collectedField.astTypeCondition));
                    }
                    nfListBuilder.add(nf);

                    if (this.options.deferSupport) {
                        nf.addDeferredExecutions(fieldGroup.deferredExecutions);
                    }
                }
                if (commonParentsGroups.size() > 1) {
                    possibleMergerList.add(new PossibleMerger(parent, resultKey));
                }
            }
        }

        private ExecutableNormalizedField createNF(CollectedFieldGroup collectedFieldGroup,
                                                   int level,
                                                   ExecutableNormalizedField parent) {

            this.fieldCount++;
            if (this.fieldCount > this.options.getMaxFieldsCount()) {
                throw new AbortExecutionException("Maximum field count exceeded. " + this.fieldCount + " > " + this.options.getMaxFieldsCount());
            }
            Field field;
            Set<GraphQLObjectType> objectTypes = collectedFieldGroup.objectTypes;
            field = collectedFieldGroup.fields.iterator().next().field;
            String fieldName = field.getName();
            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDefinition(graphQLSchema, objectTypes.iterator().next(), fieldName);

            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), CoercedVariables.of(this.coercedVariableValues.toMap()), this.options.graphQLContext, this.options.locale);
            Map<String, NormalizedInputValue> normalizedArgumentValues = null;
            if (this.normalizedVariableValues != null) {
                normalizedArgumentValues = ValuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), this.normalizedVariableValues);
            }
            ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
            return ExecutableNormalizedField.newNormalizedField()
                    .alias(field.getAlias())
                    .resolvedArguments(argumentValues)
                    .normalizedArguments(normalizedArgumentValues)
                    .astArguments(field.getArguments())
                    .objectTypeNames(objectTypeNames)
                    .fieldName(fieldName)
                    .level(level)
                    .parent(parent)
                    .build();
        }

        private List<CollectedFieldGroup> groupByCommonParents(Collection<CollectedField> fields) {
            if (this.options.deferSupport) {
                return groupByCommonParentsWithDeferSupport(fields);
            } else {
                return groupByCommonParentsNoDeferSupport(fields);
            }
        }

        private List<CollectedFieldGroup> groupByCommonParentsNoDeferSupport(Collection<CollectedField> fields) {
            ImmutableSet.Builder<GraphQLObjectType> objectTypes = ImmutableSet.builder();
            for (CollectedField collectedField : fields) {
                objectTypes.addAll(collectedField.objectTypes);
            }
            Set<GraphQLObjectType> allRelevantObjects = objectTypes.build();
            Map<GraphQLType, ImmutableList<CollectedField>> groupByAstParent = groupingBy(fields, fieldAndType -> fieldAndType.astTypeCondition);
            if (groupByAstParent.size() == 1) {
                return singletonList(new CollectedFieldGroup(ImmutableSet.copyOf(fields), allRelevantObjects, null));
            }
            ImmutableList.Builder<CollectedFieldGroup> result = ImmutableList.builder();
            for (GraphQLObjectType objectType : allRelevantObjects) {
                Set<CollectedField> relevantFields = filterSet(fields, field -> field.objectTypes.contains(objectType));
                result.add(new CollectedFieldGroup(relevantFields, singleton(objectType), null));
            }
            return result.build();
        }

        private List<CollectedFieldGroup> groupByCommonParentsWithDeferSupport(Collection<CollectedField> fields) {
            ImmutableSet.Builder<GraphQLObjectType> objectTypes = ImmutableSet.builder();
            ImmutableSet.Builder<NormalizedDeferredExecution> deferredExecutionsBuilder = ImmutableSet.builder();

            for (CollectedField collectedField : fields) {
                objectTypes.addAll(collectedField.objectTypes);

                NormalizedDeferredExecution collectedDeferredExecution = collectedField.deferredExecution;

                if (collectedDeferredExecution != null) {
                    deferredExecutionsBuilder.add(collectedDeferredExecution);
                }
            }

            Set<GraphQLObjectType> allRelevantObjects = objectTypes.build();
            Set<NormalizedDeferredExecution> deferredExecutions = deferredExecutionsBuilder.build();

            Set<String> duplicatedLabels = listDuplicatedLabels(deferredExecutions);

            if (!duplicatedLabels.isEmpty()) {
                // Query validation should pick this up
                assertShouldNeverHappen("Duplicated @defer labels are not allowed: [%s]", String.join(",", duplicatedLabels));
            }

            Map<GraphQLType, ImmutableList<CollectedField>> groupByAstParent = groupingBy(fields, fieldAndType -> fieldAndType.astTypeCondition);
            if (groupByAstParent.size() == 1) {
                return singletonList(new CollectedFieldGroup(ImmutableSet.copyOf(fields), allRelevantObjects, deferredExecutions));
            }

            ImmutableList.Builder<CollectedFieldGroup> result = ImmutableList.builder();
            for (GraphQLObjectType objectType : allRelevantObjects) {
                Set<CollectedField> relevantFields = filterSet(fields, field -> field.objectTypes.contains(objectType));

                Set<NormalizedDeferredExecution> filteredDeferredExecutions = deferredExecutions.stream()
                        .filter(filterExecutionsFromType(objectType))
                        .collect(toCollection(LinkedHashSet::new));

                result.add(new CollectedFieldGroup(relevantFields, singleton(objectType), filteredDeferredExecutions));
            }
            return result.build();
        }

        private static Predicate<NormalizedDeferredExecution> filterExecutionsFromType(GraphQLObjectType objectType) {
            String objectTypeName = objectType.getName();
            return deferredExecution -> deferredExecution.getPossibleTypes()
                    .stream()
                    .map(GraphQLObjectType::getName)
                    .anyMatch(objectTypeName::equals);
        }

        private Set<String> listDuplicatedLabels(Collection<NormalizedDeferredExecution> deferredExecutions) {
            return deferredExecutions.stream()
                    .map(NormalizedDeferredExecution::getLabel)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(toSet());
        }

        private void collectFromSelectionSet(SelectionSet selectionSet,
                                             List<CollectedField> result,
                                             GraphQLCompositeType astTypeCondition,
                                             Set<GraphQLObjectType> possibleObjects,
                                             NormalizedDeferredExecution deferredExecution
        ) {
            for (Selection<?> selection : selectionSet.getSelections()) {
                if (selection instanceof Field) {
                    collectField(result, (Field) selection, possibleObjects, astTypeCondition, deferredExecution);
                } else if (selection instanceof InlineFragment) {
                    collectInlineFragment(result, (InlineFragment) selection, possibleObjects, astTypeCondition);
                } else if (selection instanceof FragmentSpread) {
                    collectFragmentSpread(result, (FragmentSpread) selection, possibleObjects);
                }
            }
        }

        private void collectFragmentSpread(List<CollectedField> result,
                                           FragmentSpread fragmentSpread,
                                           Set<GraphQLObjectType> possibleObjects
        ) {
            if (!conditionalNodes.shouldInclude(fragmentSpread,
                    this.coercedVariableValues.toMap(),
                    this.graphQLSchema,
                    this.options.graphQLContext)) {
                return;
            }
            FragmentDefinition fragmentDefinition = assertNotNull(this.fragments.get(fragmentSpread.getName()));

            if (!conditionalNodes.shouldInclude(fragmentDefinition,
                    this.coercedVariableValues.toMap(),
                    this.graphQLSchema,
                    this.options.graphQLContext)) {
                return;
            }
            GraphQLCompositeType newAstTypeCondition = (GraphQLCompositeType) assertNotNull(this.graphQLSchema.getType(fragmentDefinition.getTypeCondition().getName()));
            Set<GraphQLObjectType> newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);

            NormalizedDeferredExecution newDeferredExecution = buildDeferredExecution(
                    fragmentSpread.getDirectives(),
                    newPossibleObjects);

            collectFromSelectionSet(fragmentDefinition.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects, newDeferredExecution);
        }

        private void collectInlineFragment(List<CollectedField> result,
                                           InlineFragment inlineFragment,
                                           Set<GraphQLObjectType> possibleObjects,
                                           GraphQLCompositeType astTypeCondition
        ) {
            if (!conditionalNodes.shouldInclude(inlineFragment, this.coercedVariableValues.toMap(), this.graphQLSchema, this.options.graphQLContext)) {
                return;
            }
            Set<GraphQLObjectType> newPossibleObjects = possibleObjects;
            GraphQLCompositeType newAstTypeCondition = astTypeCondition;

            if (inlineFragment.getTypeCondition() != null) {
                newAstTypeCondition = (GraphQLCompositeType) this.graphQLSchema.getType(inlineFragment.getTypeCondition().getName());
                newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);

            }

            NormalizedDeferredExecution newDeferredExecution = buildDeferredExecution(
                    inlineFragment.getDirectives(),
                    newPossibleObjects
            );

            collectFromSelectionSet(inlineFragment.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects, newDeferredExecution);
        }

        private NormalizedDeferredExecution buildDeferredExecution(
                List<Directive> directives,
                Set<GraphQLObjectType> newPossibleObjects) {
            if (!options.deferSupport) {
                return null;
            }

            return IncrementalUtils.createDeferredExecution(
                    this.coercedVariableValues.toMap(),
                    directives,
                    (label) -> new NormalizedDeferredExecution(label, newPossibleObjects)
            );
        }

        private void collectField(List<CollectedField> result,
                                  Field field,
                                  Set<GraphQLObjectType> possibleObjectTypes,
                                  GraphQLCompositeType astTypeCondition
        ) {
            List<Directive> directives = field.getDirectives();

            // this means there is actually no possible type for this field, and we are done
            if (possibleObjectTypes.isEmpty()) {
                return;
            }
            result.add(new CollectedField(field, possibleObjectTypes, astTypeCondition));
        }

        private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                                 GraphQLCompositeType typeCondition) {

            ImmutableSet<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition);
            if (currentOnes.isEmpty()) {
                return resolvedTypeCondition;
            }

            // Faster intersection, as either set often has a size of 1.
            return intersection(currentOnes, resolvedTypeCondition);
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
                return ImmutableSet.copyOf(ImmutableKit.map(unionTypes, GraphQLObjectType.class::cast));
            } else {
                return assertShouldNeverHappen();
            }
        }

        private static class PossibleMerger {
            ExecutableNormalizedField parent;
            String resultKey;

            public PossibleMerger(ExecutableNormalizedField parent, String resultKey) {
                this.parent = parent;
                this.resultKey = resultKey;
            }
        }

        private static class CollectedField {
            Field field;
            Set<GraphQLObjectType> objectTypes;
            GraphQLCompositeType astTypeCondition;

            public CollectedField(Field field, Set<GraphQLObjectType> objectTypes, GraphQLCompositeType astTypeCondition) {
                this.field = field;
                this.objectTypes = objectTypes;
                this.astTypeCondition = astTypeCondition;
            }
        }

        public static class CollectNFResult {
            private final Collection<NormalizedField> children;

            public CollectNFResult(Collection<NormalizedField> children) {
                this.children = children;
            }
        }

        private static class FieldAndAstParent {
            final Field field;
            final GraphQLCompositeType astParentType;

            private FieldAndAstParent(Field field, GraphQLCompositeType astParentType) {
                this.field = field;
                this.astParentType = astParentType;
            }
        }

        private static class CollectedFieldGroup {
            Set<GraphQLObjectType> objectTypes;
            Set<CollectedField> fields;
            Set<NormalizedDeferredExecution> deferredExecutions;

            public CollectedFieldGroup(Set<CollectedField> fields, Set<GraphQLObjectType> objectTypes, Set<NormalizedDeferredExecution> deferredExecutions) {
                this.fields = fields;
                this.objectTypes = objectTypes;
                this.deferredExecutions = deferredExecutions;
            }
        }
    }

}
