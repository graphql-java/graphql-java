package graphql.normalized.nf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.collect.ImmutableKit;
import graphql.execution.AbortExecutionException;
import graphql.execution.MergedField;
import graphql.execution.conditional.ConditionalNodes;
import graphql.execution.directives.QueryDirectives;
import graphql.introspection.Introspection;
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
import graphql.schema.FieldCoordinates;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.FpKit.filterSet;
import static graphql.util.FpKit.groupingBy;
import static graphql.util.FpKit.intersection;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

@ExperimentalApi
public class NormalizedDocumentFactory {

    public static class Options {


        private final GraphQLContext graphQLContext;
        private final Locale locale;
        private final int maxChildrenDepth;
        private final int maxFieldsCount;

        private final boolean deferSupport;

        /**
         * The default max fields count is 100,000.
         * This is big enough for even very large queries, but
         * can be changed via {#setDefaultOptions
         */
        public static final int DEFAULT_MAX_FIELDS_COUNT = 100_000;
        private static Options defaultOptions = new Options(GraphQLContext.getDefault(),
                Locale.getDefault(),
                Integer.MAX_VALUE,
                DEFAULT_MAX_FIELDS_COUNT,
                false);

        private Options(GraphQLContext graphQLContext,
                        Locale locale,
                        int maxChildrenDepth,
                        int maxFieldsCount,
                        boolean deferSupport) {
            this.graphQLContext = graphQLContext;
            this.locale = locale;
            this.maxChildrenDepth = maxChildrenDepth;
            this.deferSupport = deferSupport;
            this.maxFieldsCount = maxFieldsCount;
        }

        /**
         * Sets new default Options used when creating instances of {@link NormalizedDocument}.
         *
         * @param options new default options
         */
        public static void setDefaultOptions(Options options) {
            defaultOptions = Assert.assertNotNull(options);
        }


        /**
         * Returns the default options used when creating instances of {@link NormalizedDocument}.
         *
         * @return the default options
         */
        public static Options defaultOptions() {
            return defaultOptions;
        }

        /**
         * Locale to use when parsing the query.
         * <p>
         * e.g. can be passed to {@link graphql.schema.Coercing} for parsing.
         *
         * @param locale the locale to use
         *
         * @return new options object to use
         */
        public Options locale(Locale locale) {
            return new Options(this.graphQLContext, locale, this.maxChildrenDepth, this.maxFieldsCount, this.deferSupport);
        }

        /**
         * Context object to use when parsing the operation.
         * <p>
         * Can be used to intercept input values e.g. using {@link graphql.execution.values.InputInterceptor}.
         *
         * @param graphQLContext the context to use
         *
         * @return new options object to use
         */
        public Options graphQLContext(GraphQLContext graphQLContext) {
            return new Options(graphQLContext, this.locale, this.maxChildrenDepth, this.maxFieldsCount, this.deferSupport);
        }

        /**
         * Controls the maximum depth of the operation. Can be used to prevent
         * against malicious operations.
         *
         * @param maxChildrenDepth the max depth
         *
         * @return new options object to use
         */
        public Options maxChildrenDepth(int maxChildrenDepth) {
            return new Options(this.graphQLContext, this.locale, maxChildrenDepth, this.maxFieldsCount, this.deferSupport);
        }

        /**
         * Controls the maximum number of ENFs created. Can be used to prevent
         * against malicious operations.
         *
         * @param maxFieldsCount the max number of ENFs created
         *
         * @return new options object to use
         */
        public Options maxFieldsCount(int maxFieldsCount) {
            return new Options(this.graphQLContext, this.locale, this.maxChildrenDepth, maxFieldsCount, this.deferSupport);
        }

        /**
         * Controls whether defer execution is supported when creating instances of {@link NormalizedDocument}.
         *
         * @param deferSupport true to enable support for defer
         *
         * @return new options object to use
         */
        @ExperimentalApi
        public Options deferSupport(boolean deferSupport) {
            return new Options(this.graphQLContext, this.locale, this.maxChildrenDepth, this.maxFieldsCount, deferSupport);
        }

        /**
         * @return context to use during operation parsing
         *
         * @see #graphQLContext(GraphQLContext)
         */
        public GraphQLContext getGraphQLContext() {
            return graphQLContext;
        }

        /**
         * @return locale to use during operation parsing
         *
         * @see #locale(Locale)
         */
        public Locale getLocale() {
            return locale;
        }

        /**
         * @return maximum children depth before aborting parsing
         *
         * @see #maxChildrenDepth(int)
         */
        public int getMaxChildrenDepth() {
            return maxChildrenDepth;
        }

        public int getMaxFieldsCount() {
            return maxFieldsCount;
        }

    }

    private static final ConditionalNodes conditionalNodes = new ConditionalNodes();

    private NormalizedDocumentFactory() {

    }

    public static NormalizedDocument createNormalizedDocument(
            GraphQLSchema graphQLSchema,
            Document document) {
        return createNormalizedDocument(
                graphQLSchema,
                document,
                Options.defaultOptions());
    }


    public static NormalizedDocument createNormalizedDocument(GraphQLSchema graphQLSchema,
                                                              Document document,
                                                              Options options) {
        return new NormalizedDocumentFactoryImpl(
                graphQLSchema,
                document,
                options
        ).createNormalizedQueryImpl();
    }


    private static class NormalizedDocumentFactoryImpl {
        private final GraphQLSchema graphQLSchema;
        private final Document document;
        private final Options options;
        private final Map<String, FragmentDefinition> fragments;

        private final List<PossibleMerger> possibleMergerList = new ArrayList<>();

        private ImmutableListMultimap.Builder<Field, NormalizedField> fieldToNormalizedField = ImmutableListMultimap.builder();
        private ImmutableMap.Builder<NormalizedField, MergedField> normalizedFieldToMergedField = ImmutableMap.builder();
        private ImmutableMap.Builder<NormalizedField, QueryDirectives> normalizedFieldToQueryDirectives = ImmutableMap.builder();
        private ImmutableListMultimap.Builder<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields = ImmutableListMultimap.builder();

        private int fieldCount = 0;
        private int maxDepthSeen = 0;

        private final List<NormalizedField> rootEnfs = new ArrayList<>();

        private final Set<String> skipIncludeVariableNames = new LinkedHashSet<>();

        private Map<String, Boolean> assumedSkipIncludeVariableValues;

        private NormalizedDocumentFactoryImpl(
                GraphQLSchema graphQLSchema,
                Document document,
                Options options
        ) {
            this.graphQLSchema = graphQLSchema;
            this.document = document;
            this.options = options;
            this.fragments = NodeUtil.getFragmentsByName(document);
        }

        /**
         * Creates a new NormalizedDocument for the provided query
         */
        private NormalizedDocument createNormalizedQueryImpl() {
            List<NormalizedDocument.NormalizedOperationWithAssumedSkipIncludeVariables> normalizedOperations = new ArrayList<>();
            for (OperationDefinition operationDefinition : document.getDefinitionsOfType(OperationDefinition.class)) {

                assumedSkipIncludeVariableValues = null;
                skipIncludeVariableNames.clear();
                NormalizedOperation normalizedOperation = createNormalizedOperation(operationDefinition);

                if (skipIncludeVariableNames.size() == 0) {
                    normalizedOperations.add(new NormalizedDocument.NormalizedOperationWithAssumedSkipIncludeVariables(null, normalizedOperation));
                } else {
                    int combinations = (int) Math.pow(2, skipIncludeVariableNames.size());
                    for (int i = 0; i < combinations; i++) {
                        assumedSkipIncludeVariableValues = new LinkedHashMap<>();
                        int variableIndex = 0;
                        for (String variableName : skipIncludeVariableNames) {
                            assumedSkipIncludeVariableValues.put(variableName, (i & (1 << variableIndex++)) != 0);
                        }
                        NormalizedOperation operationWithAssumedVariables = createNormalizedOperation(operationDefinition);
                        normalizedOperations.add(new NormalizedDocument.NormalizedOperationWithAssumedSkipIncludeVariables(assumedSkipIncludeVariableValues, operationWithAssumedVariables));
                    }
                }
            }

            return new NormalizedDocument(
                    normalizedOperations
            );
        }

        private NormalizedOperation createNormalizedOperation(OperationDefinition operationDefinition) {
            this.rootEnfs.clear();
            this.fieldCount = 0;
            this.maxDepthSeen = 0;
            this.possibleMergerList.clear();
            fieldToNormalizedField = ImmutableListMultimap.builder();
            normalizedFieldToMergedField = ImmutableMap.builder();
            normalizedFieldToQueryDirectives = ImmutableMap.builder();
            coordinatesToNormalizedFields = ImmutableListMultimap.builder();

            buildNormalizedFieldsRecursively(null, operationDefinition, null, 0);

            for (PossibleMerger possibleMerger : possibleMergerList) {
                List<NormalizedField> childrenWithSameResultKey = possibleMerger.parent.getChildrenWithSameResultKey(possibleMerger.resultKey);
                NormalizedFieldsMerger.merge(possibleMerger.parent, childrenWithSameResultKey, graphQLSchema);
            }

            NormalizedOperation normalizedOperation = new NormalizedOperation(
                    operationDefinition.getOperation(),
                    operationDefinition.getName(),
                    new ArrayList<>(rootEnfs),
                    fieldToNormalizedField.build(),
                    normalizedFieldToMergedField.build(),
                    normalizedFieldToQueryDirectives.build(),
                    coordinatesToNormalizedFields.build(),
                    fieldCount,
                    maxDepthSeen
            );
            return normalizedOperation;
        }


        private void captureMergedField(NormalizedField enf, MergedField mergedFld) {
//            // QueryDirectivesImpl is a lazy object and only computes itself when asked for
//            QueryDirectives queryDirectives = new QueryDirectivesImpl(mergedFld, graphQLSchema, coercedVariableValues.toMap(), options.getGraphQLContext(), options.getLocale());
//            normalizedFieldToQueryDirectives.put(enf, queryDirectives);
            normalizedFieldToMergedField.put(enf, mergedFld);
        }

        private void buildNormalizedFieldsRecursively(@Nullable NormalizedField normalizedField,
                                                      @Nullable OperationDefinition operationDefinition,
                                                      @Nullable ImmutableList<CollectedField> fieldAndAstParents,
                                                      int curLevel) {
            if (this.maxDepthSeen < curLevel) {
                this.maxDepthSeen = curLevel;
                checkMaxDepthExceeded(curLevel);
            }
            Set<GraphQLObjectType> possibleObjects;
            List<CollectedField> collectedFields;

            // special handling for the root selection Set
            if (normalizedField == null) {
                GraphQLObjectType rootType = SchemaUtil.getOperationRootType(graphQLSchema, operationDefinition);
                possibleObjects = ImmutableSet.of(rootType);
                collectedFields = new ArrayList<>();
                collectFromSelectionSet(operationDefinition.getSelectionSet(), collectedFields, rootType, possibleObjects);
            } else {
                List<GraphQLFieldDefinition> fieldDefs = normalizedField.getFieldDefinitions(graphQLSchema);
                possibleObjects = resolvePossibleObjects(fieldDefs);
                if (possibleObjects.isEmpty()) {
                    return;
                }
                collectedFields = new ArrayList<>();
                for (CollectedField fieldAndAstParent : fieldAndAstParents) {
                    if (fieldAndAstParent.field.getSelectionSet() == null) {
                        continue;
                    }
                    // the AST parent comes from the previous collect from selection set call
                    // and is the type to which the field belongs (the container type of the field) and output type
                    // of the field needs to be determined based on the field name
                    GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(graphQLSchema, fieldAndAstParent.astTypeCondition, fieldAndAstParent.field.getName());
                    // it must a composite type, because the field has a selection set
                    GraphQLCompositeType selectionSetType = (GraphQLCompositeType) unwrapAll(fieldDefinition.getType());
                    this.collectFromSelectionSet(fieldAndAstParent.field.getSelectionSet(),
                            collectedFields,
                            selectionSetType,
                            possibleObjects
                    );
                }
            }

            Map<String, List<CollectedField>> fieldsByName = fieldsByResultKey(collectedFields);
            ImmutableList.Builder<NormalizedField> resultNFs = ImmutableList.builder();
            ImmutableListMultimap.Builder<NormalizedField, CollectedField> normalizedFieldToAstFields = ImmutableListMultimap.builder();
            createNFs(resultNFs, fieldsByName, normalizedFieldToAstFields, curLevel + 1, normalizedField);

            ImmutableList<NormalizedField> nextLevelChildren = resultNFs.build();
            ImmutableListMultimap<NormalizedField, CollectedField> nextLevelNormalizedFieldToAstFields = normalizedFieldToAstFields.build();

            for (NormalizedField childENF : nextLevelChildren) {
                if (normalizedField == null) {
                    // all root ENFs don't have a parent, but are collected in the rootEnfs list
                    rootEnfs.add(childENF);
                } else {
                    normalizedField.addChild(childENF);
                }
                ImmutableList<CollectedField> childFieldAndAstParents = nextLevelNormalizedFieldToAstFields.get(childENF);

                MergedField mergedField = newMergedField(childFieldAndAstParents);
                captureMergedField(childENF, mergedField);

                updateFieldToNFMap(childENF, childFieldAndAstParents);
                updateCoordinatedToNFMap(childENF);

                // recursive call
                buildNormalizedFieldsRecursively(childENF,
                        null,
                        childFieldAndAstParents,
                        curLevel + 1);
            }
        }

        private void checkMaxDepthExceeded(int depthSeen) {
            if (depthSeen > this.options.getMaxChildrenDepth()) {
                throw new AbortExecutionException("Maximum query depth exceeded. " + depthSeen + " > " + this.options.getMaxChildrenDepth());
            }
        }

        private static MergedField newMergedField(ImmutableList<CollectedField> fieldAndAstParents) {
            return MergedField.newMergedField(map(fieldAndAstParents, fieldAndAstParent -> fieldAndAstParent.field)).build();
        }

        private void updateFieldToNFMap(NormalizedField NormalizedField,
                                        ImmutableList<CollectedField> mergedField) {
            for (CollectedField astField : mergedField) {
                fieldToNormalizedField.put(astField.field, NormalizedField);
            }
        }

        private void updateCoordinatedToNFMap(NormalizedField topLevel) {
            for (String objectType : topLevel.getObjectTypeNames()) {
                FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, topLevel.getFieldName());
                coordinatesToNormalizedFields.put(coordinates, topLevel);
            }
        }


        private Map<String, List<CollectedField>> fieldsByResultKey(List<CollectedField> collectedFields) {
            Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
            for (CollectedField collectedField : collectedFields) {
                fieldsByName.computeIfAbsent(collectedField.field.getResultKey(), ignored -> new ArrayList<>()).add(collectedField);
            }
            return fieldsByName;
        }


        private void createNFs(ImmutableList.Builder<NormalizedField> nfListBuilder,
                               Map<String, List<CollectedField>> fieldsByName,
                               ImmutableListMultimap.Builder<NormalizedField, CollectedField> normalizedFieldToAstFields,
                               int level,
                               NormalizedField parent) {
            for (String resultKey : fieldsByName.keySet()) {
                List<CollectedField> fieldsWithSameResultKey = fieldsByName.get(resultKey);
                List<CollectedFieldGroup> commonParentsGroups = groupByCommonParents(fieldsWithSameResultKey);
                for (CollectedFieldGroup fieldGroup : commonParentsGroups) {
                    NormalizedField nf = createNF(fieldGroup, level, parent);
                    if (nf == null) {
                        continue;
                    }
                    for (CollectedField collectedField : fieldGroup.fields) {
                        normalizedFieldToAstFields.put(nf, collectedField);
                    }
                    nfListBuilder.add(nf);

                }
                if (commonParentsGroups.size() > 1) {
                    possibleMergerList.add(new PossibleMerger(parent, resultKey));
                }
            }
        }

        // new single ENF
        private NormalizedField createNF(CollectedFieldGroup collectedFieldGroup,
                                         int level,
                                         NormalizedField parent) {

            this.fieldCount++;
            if (this.fieldCount > this.options.getMaxFieldsCount()) {
                throw new AbortExecutionException("Maximum field count exceeded. " + this.fieldCount + " > " + this.options.getMaxFieldsCount());
            }
            Field field;
            Set<GraphQLObjectType> objectTypes = collectedFieldGroup.objectTypes;
            field = collectedFieldGroup.fields.iterator().next().field;
            List<Directive> directives = collectedFieldGroup.fields.stream().flatMap(f -> f.field.getDirectives().stream()).collect(Collectors.toList());
            String fieldName = field.getName();
            ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
            return NormalizedField.newNormalizedField()
                    .alias(field.getAlias())
                    .astArguments(field.getArguments())
                    .astDirectives(directives)
                    .objectTypeNames(objectTypeNames)
                    .fieldName(fieldName)
                    .level(level)
                    .parent(parent)
                    .build();
        }


        private List<CollectedFieldGroup> groupByCommonParents(Collection<CollectedField> fields) {
            ImmutableSet.Builder<GraphQLObjectType> objectTypes = ImmutableSet.builder();
            for (CollectedField collectedField : fields) {
                objectTypes.addAll(collectedField.objectTypes);
            }
            Set<GraphQLObjectType> allRelevantObjects = objectTypes.build();
            Map<GraphQLType, ImmutableList<CollectedField>> groupByAstParent = groupingBy(fields, fieldAndType -> fieldAndType.astTypeCondition);
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


        private void collectFromSelectionSet(SelectionSet selectionSet,
                                             List<CollectedField> result,
                                             GraphQLCompositeType astTypeCondition,
                                             Set<GraphQLObjectType> possibleObjects
        ) {
            for (Selection<?> selection : selectionSet.getSelections()) {
                if (selection instanceof Field) {
                    collectField(result, (Field) selection, possibleObjects, astTypeCondition);
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
//            if (!conditionalNodes.shouldInclude(fragmentSpread,
//                    this.coercedVariableValues.toMap(),
//                    this.graphQLSchema,
//                    this.options.graphQLContext)) {
//                return;
//            }
            FragmentDefinition fragmentDefinition = assertNotNull(this.fragments.get(fragmentSpread.getName()));

//            if (!conditionalNodes.shouldInclude(fragmentDefinition,
//                    this.coercedVariableValues.toMap(),
//                    this.graphQLSchema,
//                    this.options.graphQLContext)) {
//                return;
//            }
            GraphQLCompositeType newAstTypeCondition = (GraphQLCompositeType) assertNotNull(this.graphQLSchema.getType(fragmentDefinition.getTypeCondition().getName()));
            Set<GraphQLObjectType> newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);
            collectFromSelectionSet(fragmentDefinition.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
        }

        private void collectInlineFragment(List<CollectedField> result,
                                           InlineFragment inlineFragment,
                                           Set<GraphQLObjectType> possibleObjects,
                                           GraphQLCompositeType astTypeCondition
        ) {
//            if (!conditionalNodes.shouldInclude(inlineFragment, this.coercedVariableValues.toMap(), this.graphQLSchema, this.options.graphQLContext)) {
//                return;
//            }
            Set<GraphQLObjectType> newPossibleObjects = possibleObjects;
            GraphQLCompositeType newAstTypeCondition = astTypeCondition;

            if (inlineFragment.getTypeCondition() != null) {
                newAstTypeCondition = (GraphQLCompositeType) this.graphQLSchema.getType(inlineFragment.getTypeCondition().getName());
                newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition);

            }


            collectFromSelectionSet(inlineFragment.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
        }

        private void collectField(List<CollectedField> result,
                                  Field field,
                                  Set<GraphQLObjectType> possibleObjectTypes,
                                  GraphQLCompositeType astTypeCondition
        ) {
            Boolean shouldInclude;
            if (assumedSkipIncludeVariableValues == null) {
                if ((shouldInclude = conditionalNodes.shouldIncludeWithoutVariables(field)) == null) {

                    String skipVariableName = conditionalNodes.getSkipVariableName(field);
                    String includeVariableName = conditionalNodes.getIncludeVariableName(field);
                    if (skipVariableName != null) {
                        skipIncludeVariableNames.add(skipVariableName);
                    }
                    if (includeVariableName != null) {
                        skipIncludeVariableNames.add(includeVariableName);
                    }
                }
                if (shouldInclude != null && !shouldInclude) {
                    return;
                }
            } else {
                if (!conditionalNodes.shouldInclude(field, (Map) assumedSkipIncludeVariableValues, graphQLSchema, null)) {
                    return;
                }
            }
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
            NormalizedField parent;
            String resultKey;

            public PossibleMerger(NormalizedField parent, String resultKey) {
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

        private static class CollectedFieldGroup {
            Set<GraphQLObjectType> objectTypes;
            Set<CollectedField> fields;

            public CollectedFieldGroup(Set<CollectedField> fields, Set<GraphQLObjectType> objectTypes) {
                this.fields = fields;
                this.objectTypes = objectTypes;
            }
        }
    }

}
