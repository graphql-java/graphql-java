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
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.language.Value;
import graphql.normalized.NormalizedField;
import graphql.normalized.NormalizedField.ChildOverlappingState;
import graphql.normalized.NormalizedQueryTree;
import graphql.schema.FieldCoordinates;
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

import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.normalized.NormalizedField.ChildOverlappingState.MUTUALLY_EXCLUSIVE_OBJECTS;
import static graphql.normalized.NormalizedField.ChildOverlappingState.SAME_FIELD;
import static graphql.normalized.NormalizedField.ChildOverlappingState.UNDECIDED_OBJECTS;
import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.isNullable;
import static graphql.schema.GraphQLTypeUtil.isScalar;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.validation.ValidationError.newValidationError;
import static graphql.validation.ValidationErrorType.FieldsConflict;
import static java.lang.String.format;

public class OverlappingFields {


    private Map<Field, List<NormalizedField>> fieldToNormalizedField = new LinkedHashMap<>();
    private Map<NormalizedField, MergedField> normalizedFieldToMergedField = new LinkedHashMap<>();
    private Map<FieldCoordinates, List<NormalizedField>> coordinatesToNormalizedFields = new LinkedHashMap<>();
    private List<NormalizedField> topLevelFields = new ArrayList<>();
    private NormalizedField rootField = NormalizedField.newNormalizedField().build();

    private Map<Field, Boolean> astParentIsObject = new LinkedHashMap<>();
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();

    private GraphQLSchema schema;
    private Document document;
    private OperationDefinition operationDefinition;
    private ValidationContext validationContext;
    private ValidationErrorCollector validationErrorCollector;


    public OverlappingFields(GraphQLSchema schema,
                             Document document,
                             OperationDefinition operationDefinition,
                             ValidationContext validationContext,
                             ValidationErrorCollector validationErrorCollector) {
        this.schema = schema;
        this.document = document;
        this.operationDefinition = operationDefinition;
        this.validationContext = validationContext;
        this.validationErrorCollector = validationErrorCollector;
    }

    public NormalizedQueryTree getNormalizedTree() {
        return new NormalizedQueryTree(rootField.getChildren(),
                fieldToNormalizedField,
                normalizedFieldToMergedField,
                coordinatesToNormalizedFields);
    }

    public void visitOperationDefinition(OperationDefinition operationDefinition) {
        GraphQLObjectType rootType = (GraphQLObjectType) validationContext.getOutputType();
        // something is wrong, other validations will catch it
        if (rootType == null) {
            return;
        }
        int level = 1;
        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        Map<String, Map<GraphQLObjectType, NormalizedField>> result = new LinkedHashMap<>(rootField.getChildrenAsMap());
        visitSelectionSetImpl(operationDefinition.getSelectionSet(), result, possibleObjects, level, rootField, rootType);
        rootField.setChildren(result);
    }

    // This traverses the selection set of
    // the field, NOT the field itself
    public void visitSelectionSetOfField(Field field) {
        if (field.getSelectionSet() == null) {
            return;
        }
        // this is the parent type of the field
        GraphQLCompositeType parentTypeForTheField = validationContext.getParentType();
        // something is wrong, other validations will catch it
        if (parentTypeForTheField == null) {
            return;
        }
        GraphQLFieldDefinition fieldDef = getFieldDef(parentTypeForTheField, field);
        if (fieldDef == null) {
            return;
        }
        // this is the actual ast parent for the subselection
        if (!(fieldDef.getType() instanceof GraphQLCompositeType)) {
            return;
        }
        GraphQLCompositeType astParentType = (GraphQLCompositeType) fieldDef.getType();


        List<NormalizedField> parentNormalizedFields = fieldToNormalizedField.get(field);
        if (parentNormalizedFields == null) {
            return;
        } else {
            for (NormalizedField parentNormalizedField : parentNormalizedFields) {
                int level = parentNormalizedField.getLevel() + 1;
                GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(parentNormalizedField.getFieldDefinition().getType());
                Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType));
                Map<String, Map<GraphQLObjectType, NormalizedField>> result = new LinkedHashMap<>(parentNormalizedField.getChildrenAsMap());

                visitSelectionSetImpl(field.getSelectionSet(), result, possibleObjects, level, parentNormalizedField, astParentType);
                parentNormalizedField.replaceChildren(result);
            }
            // this means this field a top level field

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
                                     GraphQLCompositeType astParentType) {
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
                                     GraphQLCompositeType astParentType) {
        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;

        GraphQLCompositeType newParentType = astParentType;
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
                            NormalizedField parentNormalizedField,
                            GraphQLCompositeType astParentType) {
        GraphQLFieldDefinition fieldDef = getFieldDef(astParentType, field);
        if (fieldDef == null) {
            return;
        }
        String resultKey = field.getResultKey();

        result.computeIfAbsent(resultKey, ignored -> new LinkedHashMap<>());
        Map<GraphQLObjectType, NormalizedField> objectTypeToNormalizedField = result.get(resultKey);

        // if zero it means this is the first field for this resultKey
        if (objectTypeToNormalizedField.size() == 0 && parentNormalizedField != null) {
            ChildOverlappingState childOverlappingState = astParentType instanceof GraphQLObjectType ? UNDECIDED_OBJECTS : SAME_FIELD;
            parentNormalizedField.updateChildOverlappingState(resultKey, childOverlappingState);
        }
        for (GraphQLObjectType objectType : objectTypes) {

            if (objectTypeToNormalizedField.containsKey(objectType)) {
                NormalizedField existingChild = objectTypeToNormalizedField.get(objectType);
                Conflict conflict = checkIfFieldIsCompatible(field, astParentType, fieldDef.getType(), existingChild, parentNormalizedField);
                if (conflict != null) {
                    addError(FieldsConflict, conflict.fields, conflict.reason);
                    continue;
                }

                MergedField mergedField1 = normalizedFieldToMergedField.get(existingChild);
                MergedField updatedMergedField = mergedField1.transform(builder -> builder.addField(field));
                normalizedFieldToMergedField.put(existingChild, updatedMergedField);
                fieldToNormalizedField.computeIfAbsent(field, ignored -> new ArrayList<>()).add(existingChild);

            } else {
                GraphQLFieldDefinition fieldDefinition;
                if (field.getName().equals(TypeNameMetaFieldDef.getName())) {
                    fieldDefinition = TypeNameMetaFieldDef;
                } else if (field.getName().equals(Introspection.SchemaMetaFieldDef.getName())) {
                    fieldDefinition = SchemaMetaFieldDef;
                } else if (field.getName().equals(Introspection.TypeMetaFieldDef.getName())) {
                    fieldDefinition = TypeMetaFieldDef;
                } else {
                    fieldDefinition = objectType.getFieldDefinition(field.getName());
                    if (fieldDefinition == null) {
                        continue;
                    }
                }

                NormalizedField newNormalizedField = NormalizedField.newNormalizedField()
                        .alias(field.getAlias())
//                        .arguments(argumentValues)
                        .objectType(objectType)
                        .fieldDefinition(fieldDefinition)
                        .level(level)
                        // don't set the rootField as actually parent
                        .parent(parentNormalizedField == rootField ? null : parentNormalizedField)
                        .build();
                objectTypeToNormalizedField.put(objectType, newNormalizedField);
                normalizedFieldToMergedField.put(newNormalizedField, MergedField.newMergedField(field).build());
                fieldToNormalizedField.computeIfAbsent(field, ignored -> new ArrayList<>()).add(newNormalizedField);
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
                                              GraphQLCompositeType astParentType,
                                              GraphQLOutputType fieldType,
                                              NormalizedField existingNormalizedField,
                                              NormalizedField parentNormalizedField) {
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
        ChildOverlappingState childOverlappingState = existingNormalizedField.getChildOverlappingState(resultKey);
        if (childOverlappingState == MUTUALLY_EXCLUSIVE_OBJECTS) {
            if (!(astParentType instanceof GraphQLObjectType)) {
                String reason = format("%s: %s and %s are different fields", resultKey, fieldA, fieldB);
                return new Conflict(resultKey, reason, fieldA, fieldB);
            }
        } else if (childOverlappingState == SAME_FIELD) {
            conflict = checkExactlySameField(resultKey, fieldA, fieldB, typeA, typeB);
            if (conflict != null) {
                return conflict;
            }
        } else if (childOverlappingState == UNDECIDED_OBJECTS) {
            if (astParentType instanceof GraphQLObjectType) {
                if (!isExactlySameField(resultKey, fieldA, fieldB, typeA, typeB)) {
                    parentNormalizedField.updateChildOverlappingState(resultKey, MUTUALLY_EXCLUSIVE_OBJECTS);
                }
            } else {
                conflict = checkExactlySameField(resultKey, fieldA, fieldB, typeA, typeB);
                if (conflict != null) {
                    return conflict;
                }
                parentNormalizedField.updateChildOverlappingState(resultKey, SAME_FIELD);
            }
        }

        return null;
    }

    private boolean isExactlySameField(String resultKey, Field fieldA, Field fieldB, GraphQLType typeA, GraphQLType typeB) {
        return checkExactlySameField(resultKey, fieldA, fieldB, typeA, typeB) == null;
    }

    private Conflict checkExactlySameField(String resultKey, Field fieldA, Field fieldB, GraphQLType typeA, GraphQLType typeB) {
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

    public void addError(ValidationErrorType validationErrorType, List<? extends Node<?>> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node<?> node : locations) {
            locationList.add(node.getSourceLocation());
        }
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(locationList)
                .description(description));
    }

    public void addError(ValidationErrorType validationErrorType, SourceLocation location, String description) {
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(location)
                .description(description));
    }

    public void addError(ValidationError.Builder validationError) {
        validationErrorCollector.addError(validationError.queryPath(validationContext.getQueryPath()).build());
    }


}
