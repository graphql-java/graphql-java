package graphql.validation;

import graphql.Assert;
import graphql.execution.MergedField;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.AstComparator;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Value;
import graphql.normalized.NormalizedField;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.isNullable;
import static graphql.schema.GraphQLTypeUtil.isScalar;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;

public class OverlappingFields {

    private NormalizedField currentNormalizedField;

    private Map<Field, List<NormalizedField>> fieldToNormalizedField = new LinkedHashMap<>();
    private Map<NormalizedField, MergedField> normalizedFieldToMergedField = new LinkedHashMap<>();
    private Map<Field, GraphQLCompositeType> fieldToParentType = new LinkedHashMap<>();
    private List<NormalizedField> topLevelFields = new ArrayList<>();
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();

    private GraphQLSchema schema;
    private Document document;
    private ValidationContext validationContext;


    public OverlappingFields(GraphQLSchema schema, Document document, ValidationContext validationContext) {
        this.schema = schema;
        this.document = document;
        this.validationContext = validationContext;
    }


//    public void shallow(FieldCollectorNormalizedQueryParams parameters,
//                                      NormalizedField normalizedField,
//                                      MergedField mergedField,
//                                      int level) {
//        GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(normalizedField.getFieldDefinition().getType());
//        // if not composite we don't have any selectionSet because it is a Scalar or enum
//        if (!(fieldType instanceof GraphQLCompositeType)) {
//            return new CollectFieldResult(Collections.emptyList(), Collections.emptyMap());
//        }
//
//        // result key -> ObjectType -> NormalizedField
//        Map<String, Map<GraphQLObjectType, NormalizedField>> subFields = new LinkedHashMap<>();
//        Map<NormalizedField, MergedField> mergedFieldByNormalizedField = new LinkedHashMap<>();
//        List<String> visitedFragments = new ArrayList<>();
//        Set<GraphQLObjectType> possibleObjects
//                = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema()));
//        List<NormalizedField> children = subFieldsToList(subFields);
//        return new CollectFieldResult(children, mergedFieldByNormalizedField);
//    }
//
//    public void visitOperationDefinition(OperationDefinition operationDefinition,
//                                         GraphQLObjectType rootType) {
//        Map<String, Map<GraphQLObjectType, NormalizedField>> subFields = new LinkedHashMap<>();
//        Map<NormalizedField, MergedField> mergedFieldByNormalizedField = new LinkedHashMap<>();
//        List<String> visitedFragments = new ArrayList<>();
//        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
//        possibleObjects.add(rootType);
//        this.collectFields(parameters, operationDefinition.getSelectionSet(), visitedFragments, subFields, mergedFieldByNormalizedField, possibleObjects, 1, null);
//        List<NormalizedField> children = subFieldsToList(subFields);
//        return new CollectFieldResult(children, mergedFieldByNormalizedField);
//    }


    public void visitField(Field field) {
        if (field.getSelectionSet() == null) {
            return;
        }
        GraphQLCompositeType parentType = validationContext.getParentType();
        // something is wrong, other validations will catch it
        if (parentType == null) {
            return;
        }
        List<NormalizedField> parentNormalizedFields = fieldToNormalizedField.get(field);
        if (parentNormalizedFields == null) {
            // this means this field a top level field
//            NormalizedField topLevelField =
//            Map<String, Map<GraphQLObjectType, NormalizedField>> result = new LinkedHashMap<>(parentNormalizedField.getChildrenAsMap());
//            GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(parentNormalizedField.getFieldDefinition().getType());
//            Set<GraphQLObjectType> possibleObjects
//                    = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType));
//
//            visitSelectionSetImpl(field.getSelectionSet(), result, possibleObjects, 1, parentNormalizedField, parentType);
//            parentNormalizedField.replaceChildren(result);
        } else {
            for (NormalizedField parentNormalizedField : parentNormalizedFields) {
                int level = parentNormalizedField.getLevel() + 1;
                Map<String, Map<GraphQLObjectType, NormalizedField>> result = new LinkedHashMap<>(parentNormalizedField.getChildrenAsMap());
                GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(parentNormalizedField.getFieldDefinition().getType());
                Set<GraphQLObjectType> possibleObjects
                        = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType));

                visitSelectionSetImpl(field.getSelectionSet(), result, possibleObjects, level, parentNormalizedField, parentType);
                parentNormalizedField.replaceChildren(result);
            }
        }
    }

    private void visitSelectionSetImpl(SelectionSet selectionSet,
                                       Map<String, Map<GraphQLObjectType, NormalizedField>> result,
                                       Set<GraphQLObjectType> possibleObjects,
                                       int level,
                                       NormalizedField parentNormalizedField,
                                       GraphQLCompositeType astParentType
    ) {


        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                visitField((Field) selection, result, possibleObjects, level, parentNormalizedField, astParentType);
            } else if (selection instanceof InlineFragment) {
                visitInlineFragment((InlineFragment) selection, result, possibleObjects, level, parentNormalizedField, astParentType);
            } else if (selection instanceof FragmentSpread) {
                visitFragmentSpread((FragmentSpread) selection, result, possibleObjects, level, parentNormalizedField, astParentType);
            }
        }
    }

    private void visitFragmentSpread(FragmentSpread fragmentSpread,
                                     Map<String, Map<GraphQLObjectType, NormalizedField>> result,
                                     Set<GraphQLObjectType> possibleObjects,
                                     int level,
                                     NormalizedField parentNormalizedField,
                                     GraphQLCompositeType parentType) {
        FragmentDefinition fragmentDefinition = fragmentsByName.get(fragmentSpread.getName());
        if (fragmentDefinition == null) {
            return;
        }

        GraphQLCompositeType newParentType = (GraphQLCompositeType) schema.getType(fragmentDefinition.getTypeCondition().getName());
        if (newParentType == null) {
            return;
        }
        Set<GraphQLObjectType> newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newParentType);
        visitSelectionSetImpl(fragmentDefinition.getSelectionSet(), result, newPossibleObjects, level, parentNormalizedField, newParentType);
    }

    private void visitInlineFragment(InlineFragment inlineFragment,
                                     Map<String, Map<GraphQLObjectType, NormalizedField>> result,
                                     Set<GraphQLObjectType> possibleObjects,
                                     int level,
                                     NormalizedField parentNormalizedField,
                                     GraphQLCompositeType parentType) {
        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;

        GraphQLCompositeType newParentType = parentType;
        if (inlineFragment.getTypeCondition() != null) {
            GraphQLCompositeType newCondition = (GraphQLCompositeType) schema.getType(inlineFragment.getTypeCondition().getName());
            // error
            if (newCondition == null) {
                return;
            }
            newParentType = newCondition;
            newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newCondition);
        }
        visitSelectionSetImpl(inlineFragment.getSelectionSet(), result, newPossibleObjects, level, parentNormalizedField, newParentType);
    }

    private void visitField(Field field,
                            Map<String, Map<GraphQLObjectType, NormalizedField>> result,
                            Set<GraphQLObjectType> objectTypes,
                            int level,
                            GraphQLCompositeType existingParentType,
                            GraphQLCompositeType astParentType) {
        GraphQLFieldDefinition fieldDef = getFieldDef(astParentType, field);
        if (fieldDef == null) {
            return;
        }
        String resultKey = field.getResultKey();

        result.computeIfAbsent(resultKey, ignored -> new LinkedHashMap<>());
        Map<GraphQLObjectType, NormalizedField> objectTypeToNormalizedField = result.get(resultKey);

        for (GraphQLObjectType objectType : objectTypes) {

            if (objectTypeToNormalizedField.containsKey(objectType)) {
                NormalizedField existingChild = objectTypeToNormalizedField.get(objectType);
                Conflict conflict = checkIfFieldIsCompatible(field, parentType, fieldDef.getType(), existingChild, parentNormalizedField);
                if (conflict != null) {
                    // save conflict errors
                }

                MergedField mergedField1 = normalizedFieldToMergedField.get(existingChild);
                MergedField updatedMergedField = mergedField1.transform(builder -> builder.addField(field));
                normalizedFieldToMergedField.put(existingChild, updatedMergedField);

            } else {
                GraphQLFieldDefinition fieldDefinition;
                if (field.getName().equals(TypeNameMetaFieldDef.getName())) {
                    fieldDefinition = TypeNameMetaFieldDef;
                } else if (field.getName().equals(Introspection.SchemaMetaFieldDef.getName())) {
                    fieldDefinition = SchemaMetaFieldDef;
                } else if (field.getName().equals(Introspection.TypeMetaFieldDef.getName())) {
                    fieldDefinition = TypeMetaFieldDef;
                } else {
                    fieldDefinition = assertNotNull(objectType.getFieldDefinition(field.getName()), () -> String.format("no field with name %s found in object %s", field.getName(), objectType.getName()));
                }

                NormalizedField newNormalizedField = NormalizedField.newNormalizedField()
                        .alias(field.getAlias())
//                        .arguments(argumentValues)
                        .objectType(objectType)
                        .fieldDefinition(fieldDefinition)
                        .level(level)
                        .parent(parentNormalizedField)
                        .build();
                objectTypeToNormalizedField.put(objectType, newNormalizedField);
                normalizedFieldToMergedField.put(newNormalizedField, MergedField.newMergedField(field).build());
            }
        }
    }

    private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                             GraphQLCompositeType typeCondition) {

        List<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition);
        if (currentOnes.size() == 0) {
            return new LinkedHashSet<>(resolvedTypeCondition);
        }

        Set<GraphQLObjectType> result = new LinkedHashSet<>(currentOnes);
        result.retainAll(resolvedTypeCondition);
        return result;
    }

    private List<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type) {
        if (type instanceof GraphQLObjectType) {
            return Collections.singletonList((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            return schema.getImplementations((GraphQLInterfaceType) type);
        } else if (type instanceof GraphQLUnionType) {
            List types = ((GraphQLUnionType) type).getTypes();
            return new ArrayList<GraphQLObjectType>(types);
        } else {
            return Assert.assertShouldNeverHappen();
        }

    }

    private Conflict checkIfFieldIsCompatible(Field field,
                                              GraphQLCompositeType astParenType,
                                              GraphQLOutputType fieldType,
                                              NormalizedField existingNormalizedField) {
        // normally checks two different fields using they type of the field, the parent types of the field
        //
        String resultKey = field.getResultKey();

        Field fieldA = field;
        Field fieldB = normalizedFieldToMergedField.get(existingNormalizedField).getSingleField();

        GraphQLType typeA = fieldType;
        GraphQLType typeB = existingNormalizedField.getFieldType();

        Conflict conflict = checkListAndNonNullConflict(field, fieldType, existingNormalizedField);

        if (conflict != null) {
            return conflict;
        }

        typeA = unwrapAll(typeA);
        typeB = unwrapAll(typeB);

        if (checkScalarAndEnumConflict(typeA, typeB)) {
            return mkNotSameTypeError(resultKey, fieldA, fieldB, typeA, typeB);
        }

        // the rest of the checks are only needed if both fields need to be
        // exactly the same: same field name with same arguments
        // Both fields needs to be exactly the same if at execution time
        // both could be valid and therefore needed to be executed together
        // The only case where they don't need to be the same is
        // for both parents being Object types

        GraphQLType parentTypeA = astParenType;
        existingNormalizedField.
                GraphQLType parentTypeB = unwrapAll(parentNormalizedField.getFieldType());

        if (!sameType(parentTypeA, parentTypeB) &&
                parentTypeA instanceof GraphQLObjectType &&
                parentTypeB instanceof GraphQLObjectType) {

            MergedField mergedField = normalizedFieldToMergedField.get(existingNormalizedField);
            MergedField newMergedField = mergedField.transform(builder -> builder.addField(field));
            normalizedFieldToMergedField.put(existingNormalizedField, newMergedField);
            return null;
        }

        String fieldNameA = fieldA.getName();
        String fieldNameB = fieldB.getName();

        if (!fieldNameA.equals(fieldNameB)) {
            String reason = format("%s: %s and %s are different fields", resultKey, fieldNameA, fieldNameB);
            return new Conflict(resultKey, reason, fieldA, fieldB);
        }

        if (!sameType(typeA, typeB)) {
            return mkNotSameTypeError(resultKey, fieldA, fieldB, typeA, typeB);
        }

        if (!sameArguments(fieldA.getArguments(), fieldB.getArguments())) {
            String reason = format("%s: they have differing arguments", resultKey);
            return new Conflict(resultKey, reason, fieldA, fieldB);
        }

        return null;
    }

    private Conflict mkNotSameTypeError(String responseName, Field fieldA, Field fieldB, GraphQLType typeA, GraphQLType typeB) {
        String name1 = typeA != null ? simplePrint(typeA) : "null";
        String name2 = typeB != null ? simplePrint(typeB) : "null";
        String reason = format("%s: they return differing types %s and %s", responseName, name1, name2);
        return new Conflict(responseName, reason, fieldA, fieldB);
    }


    private boolean checkScalarAndEnumConflict(GraphQLType typeA, GraphQLType typeB) {
        if (isScalar(typeA) || isScalar(typeB)) {
            if (!sameType(typeA, typeB)) {
                return true;
            }
        }
        if (isEnum(typeA) || isEnum(typeB)) {
            if (!sameType(typeA, typeB)) {
                return true;
            }
        }
        return false;
    }

    private String joinReasons(List<Conflict> conflicts) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        for (Conflict conflict : conflicts) {
            result.append(conflict.reason);
            result.append(", ");
        }
        result.delete(result.length() - 2, result.length());
        result.append(")");
        return result.toString();
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean sameType(GraphQLType type1, GraphQLType type2) {
        if (type1 == null || type2 == null) {
            return true;
        }
        return type1.equals(type2);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean sameValue(Value value1, Value value2) {
        if (value1 == null && value2 == null) {
            return true;
        }
        if (value1 == null) {
            return false;
        }
        if (value2 == null) {
            return false;
        }
        return new AstComparator().isEqual(value1, value2);
    }

    private boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!sameValue(argument.getValue(), matchedArgument.getValue())) {
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


    private Conflict checkListAndNonNullConflict(Field field, GraphQLType fieldType, NormalizedField normalizedField) {
        String resultKey = field.getResultKey();

        Field fieldA = field;
        Field fieldB = normalizedFieldToMergedField.get(normalizedField).getSingleField();
        GraphQLType typeA = fieldType;
        GraphQLType typeB = normalizedField.getFieldDefinition().getType();

        while (true) {
            if (isNonNull(typeA) || isNonNull(typeB)) {
                if (isNullable(typeA) || isNullable(typeB)) {
                    String reason = format("%s: fields have different nullability shapes", resultKey);
                    return new Conflict(resultKey, reason, fieldA, fieldB);
                }
            }
            if (isList(typeA) || isList(typeB)) {
                if (!isList(typeA) || !isList(typeB)) {
                    String reason = format("%s: fields have different list shapes", resultKey);
                    return new Conflict(resultKey, reason, fieldA, fieldB);
                }
            }
            if (isNotWrapped(typeA) && isNotWrapped(typeB)) {
                break;
            }
            typeA = unwrapOne(typeA);
            typeB = unwrapOne(typeB);
        }
        return null;
    }

    private static class Conflict {
        final String responseName;
        final String reason;
        final List<Field> fields = new ArrayList<>();

        public Conflict(String responseName, String reason, Field field1, Field field2) {
            this.responseName = responseName;
            this.reason = reason;
            this.fields.add(field1);
            this.fields.add(field2);
        }

        public Conflict(String responseName, String reason, List<Field> fields) {
            this.responseName = responseName;
            this.reason = reason;
            this.fields.addAll(fields);
        }

    }

    // copied from TraversalContext
    private GraphQLFieldDefinition getFieldDef(GraphQLType parentType, Field field) {
        if (schema.getQueryType().equals(parentType)) {
            if (field.getName().equals(SchemaMetaFieldDef.getName())) {
                return SchemaMetaFieldDef;
            }
            if (field.getName().equals(TypeMetaFieldDef.getName())) {
                return TypeMetaFieldDef;
            }
        }
        if (field.getName().equals(TypeNameMetaFieldDef.getName())
                && (parentType instanceof GraphQLObjectType ||
                parentType instanceof GraphQLInterfaceType ||
                parentType instanceof GraphQLUnionType)) {
            return TypeNameMetaFieldDef;
        }
        if (parentType instanceof GraphQLFieldsContainer) {
            return schema.getFieldVisibility().getFieldDefinition((GraphQLFieldsContainer) parentType, field.getName());
        }
        return null;
    }


}
