package graphql.validation.rules;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.execution.TypeFromAST;
import graphql.language.Argument;
import graphql.language.AstComparator;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.isNullable;
import static graphql.schema.GraphQLTypeUtil.isScalar;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.util.FpKit.filterSet;
import static graphql.util.FpKit.groupingBy;
import static graphql.validation.ValidationErrorType.FieldsConflict;
import static java.lang.String.format;

@Internal
public class OverlappingFieldsCanBeMerged extends AbstractRule {


    private final Set<Set<FieldAndType>> sameResponseShapeChecked = new LinkedHashSet<>();
    private final Set<Set<FieldAndType>> sameForCommonParentsChecked = new LinkedHashSet<>();

    public OverlappingFieldsCanBeMerged(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void leaveSelectionSet(SelectionSet selectionSet) {
        Map<String, Set<FieldAndType>> fieldMap = new LinkedHashMap<>();
        Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
        collectFields(fieldMap, selectionSet, getValidationContext().getOutputType(), visitedFragmentSpreads);
        List<Conflict> conflicts = findConflicts(fieldMap);
        for (Conflict conflict : conflicts) {
            addError(FieldsConflict, conflict.fields, conflict.reason);
        }
    }

    private void collectFields(Map<String, Set<FieldAndType>> fieldMap, SelectionSet selectionSet, GraphQLType parentType, Set<String> visitedFragmentSpreads) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectFieldsForField(fieldMap, parentType, (Field) selection);

            } else if (selection instanceof InlineFragment) {
                collectFieldsForInlineFragment(fieldMap, visitedFragmentSpreads, parentType, (InlineFragment) selection);

            } else if (selection instanceof FragmentSpread) {
                collectFieldsForFragmentSpread(fieldMap, visitedFragmentSpreads, (FragmentSpread) selection);
            }
        }
    }

    private void collectFieldsForFragmentSpread(Map<String, Set<FieldAndType>> fieldMap, Set<String> visitedFragmentSpreads, FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragment == null) {
            return;
        }
        if (visitedFragmentSpreads.contains(fragment.getName())) {
            return;
        }
        visitedFragmentSpreads.add(fragment.getName());
        GraphQLType graphQLType = TypeFromAST.getTypeFromAST(getValidationContext().getSchema(),
                fragment.getTypeCondition());
        collectFields(fieldMap, fragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
    }

    private void collectFieldsForInlineFragment(Map<String, Set<FieldAndType>> fieldMap, Set<String> visitedFragmentSpreads, GraphQLType parentType, InlineFragment inlineFragment) {
        GraphQLType graphQLType = inlineFragment.getTypeCondition() != null
                ? TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), inlineFragment.getTypeCondition())
                : parentType;
        collectFields(fieldMap, inlineFragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
    }

    private void collectFieldsForField(Map<String, Set<FieldAndType>> fieldMap, GraphQLType parentType, Field field) {
        String responseName = field.getResultKey();
        if (!fieldMap.containsKey(responseName)) {
            fieldMap.put(responseName, new LinkedHashSet<>());
        }
        GraphQLOutputType fieldType = null;
        GraphQLUnmodifiedType unwrappedParent = unwrapAll(parentType);
        if (unwrappedParent instanceof GraphQLFieldsContainer) {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) unwrappedParent;
            GraphQLFieldDefinition fieldDefinition = getVisibleFieldDefinition(fieldsContainer, field);
            fieldType = fieldDefinition != null ? fieldDefinition.getType() : null;
        }
        fieldMap.get(responseName).add(new FieldAndType(field, fieldType, parentType));
    }

    private GraphQLFieldDefinition getVisibleFieldDefinition(GraphQLFieldsContainer fieldsContainer, Field field) {
        return getValidationContext().getSchema().getCodeRegistry().getFieldVisibility().getFieldDefinition(fieldsContainer, field.getName());
    }


    private List<Conflict> findConflicts(Map<String, Set<FieldAndType>> fieldMap) {
        List<Conflict> result = new ArrayList<>();
        sameResponseShapeByName(fieldMap, result);
        sameForCommonParentsByName(fieldMap, result);
        return result;
    }

    private void sameResponseShapeByName(Map<String, Set<FieldAndType>> fieldMap, List<Conflict> conflictsResult) {
        for (Map.Entry<String, Set<FieldAndType>> entry : fieldMap.entrySet()) {
            if (sameResponseShapeChecked.contains(entry.getValue())) {
                continue;
            }
            sameResponseShapeChecked.add(entry.getValue());
            Conflict conflict = requireSameOutputTypeShape(entry.getKey(), entry.getValue());
            if (conflict != null) {
                conflictsResult.add(conflict);
                continue;
            }
            Map<String, Set<FieldAndType>> subSelections = mergeSubSelections(entry.getValue());
            sameResponseShapeByName(subSelections, conflictsResult);
        }
    }

    private Map<String, Set<FieldAndType>> mergeSubSelections(Set<FieldAndType> sameNameFields) {
        Map<String, Set<FieldAndType>> fieldMap = new LinkedHashMap<>();
        for (FieldAndType fieldAndType : sameNameFields) {
            if (fieldAndType.field.getSelectionSet() != null) {
                Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
                collectFields(fieldMap, fieldAndType.field.getSelectionSet(), fieldAndType.graphQLType, visitedFragmentSpreads);
            }
        }
        return fieldMap;
    }

    private void sameForCommonParentsByName(Map<String, Set<FieldAndType>> fieldMap, List<Conflict> conflictsResult) {
        for (Map.Entry<String, Set<FieldAndType>> entry : fieldMap.entrySet()) {
            List<Set<FieldAndType>> groups = groupByCommonParents(entry.getValue());
            for (Set<FieldAndType> group : groups) {
                if (sameForCommonParentsChecked.contains(group)) {
                    continue;
                }
                sameForCommonParentsChecked.add(group);
                Conflict conflict = requireSameNameAndArguments(entry.getKey(), group);
                if (conflict != null) {
                    conflictsResult.add(conflict);
                    continue;
                }
                Map<String, Set<FieldAndType>> subSelections = mergeSubSelections(group);
                sameForCommonParentsByName(subSelections, conflictsResult);
            }
        }
    }

    private List<Set<FieldAndType>> groupByCommonParents(Set<FieldAndType> fields) {
        Set<FieldAndType> abstractTypes = filterSet(fields, fieldAndType -> isInterfaceOrUnion(fieldAndType.parentType));
        Set<FieldAndType> concreteTypes = filterSet(fields, fieldAndType -> fieldAndType.parentType instanceof GraphQLObjectType);
        if (concreteTypes.isEmpty()) {
            return Collections.singletonList(abstractTypes);
        }
        Map<GraphQLType, ImmutableList<FieldAndType>> groupsByConcreteParent = groupingBy(concreteTypes, fieldAndType -> fieldAndType.parentType);
        List<Set<FieldAndType>> result = new ArrayList<>();
        for (ImmutableList<FieldAndType> concreteGroup : groupsByConcreteParent.values()) {
            Set<FieldAndType> oneResultGroup = new LinkedHashSet<>(concreteGroup);
            oneResultGroup.addAll(abstractTypes);
            result.add(oneResultGroup);
        }
        return result;
    }

    private boolean isInterfaceOrUnion(GraphQLType type) {
        return type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType;
    }

    private Conflict requireSameNameAndArguments(String responseName, Set<FieldAndType> fieldAndTypes) {
        if (fieldAndTypes.size() <= 1) {
            return null;
        }
        String name = null;
        List<Argument> arguments = null;
        List<Field> fields = new ArrayList<>();
        for (FieldAndType fieldAndType : fieldAndTypes) {
            Field field = fieldAndType.field;
            fields.add(field);
            if (name == null) {
                name = field.getName();
                arguments = field.getArguments();
                continue;
            }
            if (!field.getName().equals(name)) {
                String reason = format("%s: %s and %s are different fields", responseName, name, field.getName());
                return new Conflict(responseName, reason, fields);
            }
            if (!sameArguments(field.getArguments(), arguments)) {
                String reason = format("%s: they have differing arguments", responseName);
                return new Conflict(responseName, reason, fields);
            }

        }
        return null;
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


    private Conflict requireSameOutputTypeShape(String responseName, Set<FieldAndType> fieldAndTypes) {
        if (fieldAndTypes.size() <= 1) {
            return null;
        }
        List<Field> fields = new ArrayList<>();
        GraphQLType typeAOriginal = null;
        for (FieldAndType fieldAndType : fieldAndTypes) {
            fields.add(fieldAndType.field);
            if (typeAOriginal == null) {
                typeAOriginal = fieldAndType.graphQLType;
                continue;
            }
            GraphQLType typeA = typeAOriginal;
            GraphQLType typeB = fieldAndType.graphQLType;
            while (true) {
                if (isNonNull(typeA) || isNonNull(typeB)) {
                    if (isNullable(typeA) || isNullable(typeB)) {
                        String reason = format("%s: fields have different nullability shapes", responseName);
                        return new Conflict(responseName, reason, fields);
                    }
                }
                if (isList(typeA) || isList(typeB)) {
                    if (!isList(typeA) || !isList(typeB)) {
                        String reason = format("%s: fields have different list shapes", responseName);
                        return new Conflict(responseName, reason, fields);
                    }
                }
                if (isNotWrapped(typeA) && isNotWrapped(typeB)) {
                    break;
                }
                typeA = unwrapOne(typeA);
                typeB = unwrapOne(typeB);
            }
            if (isScalar(typeA) || isScalar(typeB)) {
                if (!sameType(typeA, typeB)) {
                    return mkNotSameTypeError(responseName, fields, typeA, typeB);
                }
            }
            if (isEnum(typeA) || isEnum(typeB)) {
                if (!sameType(typeA, typeB)) {
                    return mkNotSameTypeError(responseName, fields, typeA, typeB);
                }
            }
        }
        return null;
    }

    private Conflict mkNotSameTypeError(String responseName, List<Field> fields, GraphQLType typeA, GraphQLType typeB) {
        String name1 = typeA != null ? simplePrint(typeA) : "null";
        String name2 = typeB != null ? simplePrint(typeB) : "null";
        String reason = format("%s: they return differing types %s and %s", responseName, name1, name2);
        return new Conflict(responseName, reason, fields);
    }


    private boolean sameType(GraphQLType type1, GraphQLType type2) {
        if (type1 == null || type2 == null) {
            return true;
        }
        return type1.equals(type2);
    }


    private static class FieldAndType {
        final Field field;
        final GraphQLType graphQLType;
        final GraphQLType parentType;

        public FieldAndType(Field field, GraphQLType graphQLType, GraphQLType parentType) {
            this.field = field;
            this.graphQLType = graphQLType;
            this.parentType = parentType;
        }

        @Override
        public String toString() {
            return "FieldAndType{" +
                    "field=" + field +
                    ", graphQLType=" + graphQLType +
                    ", parentType=" + parentType +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FieldAndType that = (FieldAndType) o;

            return field != null ? field.equals(that.field) : that.field == null;
        }

        @Override
        public int hashCode() {
            return field != null ? field.hashCode() : 0;
        }
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


}
