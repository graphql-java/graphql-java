package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.nextgen.Common;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.AstComparator;
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
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

@Internal
public class PreNormalizedQueryFactory {

    private final ValuesResolver valuesResolver = new ValuesResolver();

    private static class FieldCollectorNormalizedQueryParams {
        private final GraphQLSchema graphQLSchema;
        private final Map<String, FragmentDefinition> fragmentsByName;

        public GraphQLSchema getGraphQLSchema() {
            return graphQLSchema;
        }

        public Map<String, FragmentDefinition> getFragmentsByName() {
            return fragmentsByName;
        }

        private FieldCollectorNormalizedQueryParams(GraphQLSchema graphQLSchema,
                                                    Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = fragmentsByName;
            this.graphQLSchema = graphQLSchema;
        }

        public static Builder newParameters() {
            return new Builder();
        }

        public static class Builder {
            private GraphQLSchema graphQLSchema;
            private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();

            private Builder() {

            }

            public Builder schema(GraphQLSchema graphQLSchema) {
                this.graphQLSchema = graphQLSchema;
                return this;
            }

            public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
                this.fragmentsByName.putAll(fragmentsByName);
                return this;
            }


            public FieldCollectorNormalizedQueryParams build() {
                Assert.assertNotNull(graphQLSchema, () -> "You must provide a schema");
                return new FieldCollectorNormalizedQueryParams(graphQLSchema, fragmentsByName);
            }

        }
    }


    public static PreNormalizedQuery createPreNormalizedQuery(GraphQLSchema graphQLSchema,
                                                              Document document,
                                                              String operationName) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new PreNormalizedQueryFactory().createPreNormalizedQueryImpl(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName);
    }


    private PreNormalizedQuery createPreNormalizedQueryImpl(GraphQLSchema graphQLSchema,
                                                            OperationDefinition operationDefinition,
                                                            Map<String, FragmentDefinition> fragments
    ) {
        FieldCollectorNormalizedQueryParams parameters = FieldCollectorNormalizedQueryParams
                .newParameters()
                .fragments(fragments)
                .schema(graphQLSchema)
                .build();


        GraphQLObjectType rootType = Common.getOperationRootType(graphQLSchema, operationDefinition);

        CollectFieldResult collectFromOperationResult = collectFromOperation(parameters, operationDefinition, rootType);

        ImmutableListMultimap.Builder<Field, PreNormalizedField> fieldToNormalizedField = ImmutableListMultimap.builder();
        ImmutableMap.Builder<PreNormalizedField, MergedField> normalizedFieldToMergedField = ImmutableMap.builder();
        ImmutableListMultimap.Builder<FieldCoordinates, PreNormalizedField> coordinatesToNormalizedFields = ImmutableListMultimap.builder();

        for (PreNormalizedField topLevel : collectFromOperationResult.children) {
            ImmutableList<Field> mergedField = collectFromOperationResult.normalizedFieldToAstFields.get(topLevel);
            normalizedFieldToMergedField.put(topLevel, MergedField.newMergedField(mergedField).build());
            updateFieldToNFMap(topLevel, mergedField, fieldToNormalizedField);
            updateCoordinatedToNFMap(coordinatesToNormalizedFields, topLevel);

            buildFieldWithChildren(topLevel,
                    mergedField,
                    parameters,
                    fieldToNormalizedField,
                    normalizedFieldToMergedField,
                    coordinatesToNormalizedFields,
                    1);

        }
        return new PreNormalizedQuery(
                new ArrayList<>(collectFromOperationResult.children),
                fieldToNormalizedField.build(),
                normalizedFieldToMergedField.build(),
                coordinatesToNormalizedFields.build(),
                ImmutableList.copyOf(operationDefinition.getVariableDefinitions()));
    }


    private void buildFieldWithChildren(PreNormalizedField field,
                                        ImmutableList<Field> mergedField,
                                        FieldCollectorNormalizedQueryParams fieldCollectorNormalizedQueryParams,
                                        ImmutableListMultimap.Builder<Field, PreNormalizedField> fieldNormalizedField,
                                        ImmutableMap.Builder<PreNormalizedField, MergedField> normalizedFieldToMergedField,
                                        ImmutableListMultimap.Builder<FieldCoordinates, PreNormalizedField> coordinatesToNormalizedFields,
                                        int curLevel) {
        CollectFieldResult nextLevel = collectFromMergedField(fieldCollectorNormalizedQueryParams, field, mergedField, curLevel + 1);
        for (PreNormalizedField child : nextLevel.children) {

            field.addChild(child);
            ImmutableList<Field> mergedFieldForChild = nextLevel.normalizedFieldToAstFields.get(child);
            normalizedFieldToMergedField.put(child, MergedField.newMergedField(mergedFieldForChild).build());
            updateFieldToNFMap(child, mergedFieldForChild, fieldNormalizedField);
            updateCoordinatedToNFMap(coordinatesToNormalizedFields, child);

            buildFieldWithChildren(child,
                    mergedFieldForChild,
                    fieldCollectorNormalizedQueryParams,
                    fieldNormalizedField,
                    normalizedFieldToMergedField,
                    coordinatesToNormalizedFields,
                    curLevel + 1);
        }
    }

    private void updateFieldToNFMap(PreNormalizedField normalizedField,
                                    ImmutableList<Field> mergedField,
                                    ImmutableListMultimap.Builder<Field, PreNormalizedField> fieldToNormalizedField) {
        for (Field astField : mergedField) {
            fieldToNormalizedField.put(astField, normalizedField);
        }
    }

    private void updateCoordinatedToNFMap(ImmutableListMultimap.Builder<FieldCoordinates, PreNormalizedField> coordinatesToNormalizedFields, PreNormalizedField topLevel) {
        for (String objectType : topLevel.getObjectTypeNames()) {
            FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, topLevel.getFieldName());
            coordinatesToNormalizedFields.put(coordinates, topLevel);
        }
    }


    public static class CollectFieldResult {
        private final Collection<PreNormalizedField> children;
        private final ImmutableListMultimap<PreNormalizedField, Field> normalizedFieldToAstFields;

        public CollectFieldResult(Collection<PreNormalizedField> children, ImmutableListMultimap<PreNormalizedField, Field> normalizedFieldToAstFields) {
            this.children = children;
            this.normalizedFieldToAstFields = normalizedFieldToAstFields;
        }
    }


    public CollectFieldResult collectFromMergedField(FieldCollectorNormalizedQueryParams parameters,
                                                     PreNormalizedField normalizedField,
                                                     ImmutableList<Field> mergedField,
                                                     int level) {
        GraphQLUnmodifiedType fieldType = unwrapAll(normalizedField.getType(parameters.getGraphQLSchema()));
        // if not composite we don't have any selectionSet because it is a Scalar or enum
        if (!(fieldType instanceof GraphQLCompositeType)) {
            return new CollectFieldResult(Collections.emptyList(), ImmutableListMultimap.of());
        }

        Multimap<String, PreNormalizedField> subFields = LinkedHashMultimap.create();
        ImmutableListMultimap.Builder<PreNormalizedField, Field> mergedFieldByNormalizedField = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects = resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema());
        for (Field field : mergedField) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFromSelectionSet(parameters,
                    field.getSelectionSet(),
                    subFields,
                    mergedFieldByNormalizedField,
                    possibleObjects,
                    level,
                    normalizedField,
                    new SingleFieldCondition()
            );
        }
        return new CollectFieldResult(new LinkedHashSet<>(subFields.values()), mergedFieldByNormalizedField.build());
    }

    public CollectFieldResult collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                   OperationDefinition operationDefinition,
                                                   GraphQLObjectType rootType) {

        Multimap<String, PreNormalizedField> subFields = LinkedHashMultimap.create();
        ImmutableListMultimap.Builder<PreNormalizedField, Field> normalizedFieldToAstFields = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        IncludeCondition includeCondition = IncludeCondition.DEFAULT_CONDITION;
        SingleFieldCondition singleFieldCondition = new SingleFieldCondition();
        this.collectFromSelectionSet(parameters, operationDefinition.getSelectionSet(), subFields, normalizedFieldToAstFields, possibleObjects, 1, null, singleFieldCondition);
        return new CollectFieldResult(subFields.values(), normalizedFieldToAstFields.build());
    }


    private void collectFromSelectionSet(FieldCollectorNormalizedQueryParams parameters,
                                         SelectionSet selectionSet,
                                         Multimap<String, PreNormalizedField> result,
                                         ImmutableListMultimap.Builder<PreNormalizedField, Field> mergedFieldByNormalizedField,
                                         Set<GraphQLObjectType> possibleObjects,
                                         int level,
                                         PreNormalizedField parent,
                                         SingleFieldCondition includeCondition) {

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, result, mergedFieldByNormalizedField, (Field) selection, possibleObjects, level, parent, includeCondition);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, result, mergedFieldByNormalizedField, (InlineFragment) selection, possibleObjects, level, parent, includeCondition);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, result, mergedFieldByNormalizedField, (FragmentSpread) selection, possibleObjects, level, parent, includeCondition);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorNormalizedQueryParams parameters,
                                       Multimap<String, PreNormalizedField> result,
                                       ImmutableListMultimap.Builder<PreNormalizedField, Field> mergedFieldByNormalizedField,
                                       FragmentSpread fragmentSpread,
                                       Set<GraphQLObjectType> possibleObjects,
                                       int level,
                                       PreNormalizedField parent,
                                       SingleFieldCondition includeCondition) {
        if (!shouldInclude(fragmentSpread.getDirectives())) {
            return;
        }
        SingleFieldCondition newIncludeCondition = updateIncludeCondition(includeCondition, fragmentSpread.getDirectives());
        FragmentDefinition fragmentDefinition = assertNotNull(parameters.getFragmentsByName().get(fragmentSpread.getName()));

        if (!shouldInclude(fragmentDefinition.getDirectives())) {
            return;
        }
        newIncludeCondition = updateIncludeCondition(newIncludeCondition, fragmentDefinition.getDirectives());
        GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName());
        Set<GraphQLObjectType> newConditions = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
        collectFromSelectionSet(parameters, fragmentDefinition.getSelectionSet(), result, mergedFieldByNormalizedField, newConditions, level, parent, newIncludeCondition);
    }

    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
                                       Multimap<String, PreNormalizedField> result,
                                       ImmutableListMultimap.Builder<PreNormalizedField, Field> mergedFieldByNormalizedField,
                                       InlineFragment inlineFragment,
                                       Set<GraphQLObjectType> possibleObjects,
                                       int level,
                                       PreNormalizedField parent,
                                       SingleFieldCondition includeCondition) {
        if (!shouldInclude(inlineFragment.getDirectives())) {
            return;
        }
        SingleFieldCondition newIncludeCondition = updateIncludeCondition(includeCondition, inlineFragment.getDirectives());
        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;

        if (inlineFragment.getTypeCondition() != null) {
            GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
            newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());

        }
        collectFromSelectionSet(parameters, inlineFragment.getSelectionSet(), result, mergedFieldByNormalizedField, newPossibleObjects, level, parent, newIncludeCondition);
    }

    private void collectField(FieldCollectorNormalizedQueryParams parameters,
                              Multimap<String, PreNormalizedField> result,
                              ImmutableListMultimap.Builder<PreNormalizedField, Field> normalizedFieldToMergedField,
                              Field field,
                              Set<GraphQLObjectType> objectTypes,
                              int level,
                              PreNormalizedField parent,
                              SingleFieldCondition includeCondition) {
        if (!shouldInclude(field.getDirectives())) {
            return;
        }
        if (objectTypes.size() == 0) {
            return;
        }
        SingleFieldCondition newFieldCondition = updateIncludeCondition(includeCondition, field.getDirectives());
        String resultKey = field.getResultKey();
        String fieldName = field.getName();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), objectTypes.iterator().next(), fieldName);

        if (result.containsKey(resultKey)) {
            Collection<PreNormalizedField> existingNFs = result.get(resultKey);
            PreNormalizedField matchingNF = findMatchingNF(parameters.getGraphQLSchema(), existingNFs, fieldDefinition, field.getArguments());
            if (matchingNF != null) {
                matchingNF.addObjectTypeNames(map(objectTypes, GraphQLObjectType::getName));
                normalizedFieldToMergedField.put(matchingNF, field);
                matchingNF.getIncludeCondition().addField(newFieldCondition);
                return;
            }
        }
        // this means we have no existing NF
        Map<String, PreNormalizedInputValue> normalizedArgumentValues = emptyMap();
//        normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getNormalizedVariableValues());
        ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
        PreNormalizedField normalizedField = PreNormalizedField.newPreNormalizedField()
                .alias(field.getAlias())
                .normalizedArguments(normalizedArgumentValues)
                .astArguments(field.getArguments())
                .objectTypeNames(objectTypeNames)
                .fieldName(fieldName)
                .level(level)
                .parent(parent)
                .includeCondition(new IncludeCondition(newFieldCondition))
                .build();

        result.put(resultKey, normalizedField);
        normalizedFieldToMergedField.put(normalizedField, field);
    }

    private PreNormalizedField findMatchingNF(GraphQLSchema schema, Collection<PreNormalizedField> normalizedFields, GraphQLFieldDefinition fieldDefinition, List<Argument> arguments) {
        for (PreNormalizedField nf : normalizedFields) {
            GraphQLFieldDefinition nfFieldDefinition = nf.getOneFieldDefinition(schema);
            // same field name
            if (!nfFieldDefinition.getName().equals(fieldDefinition.getName())) {
                continue;
            }
            // same type
            if (!simplePrint(nfFieldDefinition.getType()).equals(simplePrint(fieldDefinition.getType()))) {
                continue;
            }
            // same arguments
            if (!sameArguments(nf.getAstArguments(), arguments)) {
                continue;
            }
            return nf;
        }
        return null;
    }

    // copied from graphql.validation.rules.OverlappingFieldsCanBeMerged
    private boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!AstComparator.sameValue(argument.getValue(), matchedArgument.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }


    private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                             GraphQLCompositeType typeCondition,
                                                             GraphQLSchema graphQLSchema) {

        ImmutableSet<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition, graphQLSchema);
        if (currentOnes.size() == 0) {
            return resolvedTypeCondition;
        }
        return Sets.intersection(currentOnes, resolvedTypeCondition);
    }

    private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type, GraphQLSchema graphQLSchema) {
        if (type instanceof GraphQLObjectType) {
            return ImmutableSet.of((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            return ImmutableSet.copyOf(graphQLSchema.getImplementations((GraphQLInterfaceType) type));
        } else if (type instanceof GraphQLUnionType) {
            List types = ((GraphQLUnionType) type).getTypes();
            return ImmutableSet.copyOf((types));
        } else {
            return Assert.assertShouldNeverHappen();
        }

    }

    private SingleFieldCondition updateIncludeCondition(SingleFieldCondition singleFieldCondition, List<Directive> directives) {
        Directive skipDirective = NodeUtil.findNodeByName(directives, SkipDirective.getName());
        SingleFieldCondition result = new SingleFieldCondition(singleFieldCondition.getVarNames());
        if (skipDirective != null) {
            String skipVarName = getVariableName(skipDirective);
            if (skipVarName != null) {
                result.addSkipVar(skipVarName);
            }
        }
        Directive includeDirective = NodeUtil.findNodeByName(directives, IncludeDirective.getName());
        if (includeDirective != null) {
            String includeVarName = getVariableName(includeDirective);
            if (includeVarName != null) {
                result.addIncludeVar(includeVarName);
            }
        }
        return result;
    }

    public boolean shouldInclude(List<Directive> directives) {
        Directive skipDirective = NodeUtil.findNodeByName(directives, SkipDirective.getName());
        boolean skip = false;
        if (skipDirective != null) {
            skip = getIfValue(skipDirective, false);
        }
        boolean include = true;
        Directive includeDirective = NodeUtil.findNodeByName(directives, IncludeDirective.getName());
        if (includeDirective != null) {
            include = getIfValue(includeDirective, true);
        }
        return !skip && include;
    }


    private boolean getIfValue(Directive directive, boolean defaultValue) {
        Argument argument = directive.getArgument("if");
        Value value = argument.getValue();
        if (value instanceof BooleanValue) {
            return ((BooleanValue) value).isValue();
        } else if (value instanceof VariableReference) {
            return defaultValue;
        } else {
            return Assert.assertShouldNeverHappen();
        }
    }

    private String getVariableName(Directive directive) {
        Argument argument = directive.getArgument("if");
        Value value = argument.getValue();
        if (value instanceof VariableReference) {
            return ((VariableReference) value).getName();
        } else {
            return null;
        }
    }


}
