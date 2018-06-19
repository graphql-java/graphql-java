package graphql.validation.rules;


import graphql.execution.TypeFromAST;
import graphql.language.Argument;
import graphql.language.AstComparator;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Value;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.validation.ValidationErrorType.FieldsConflict;

public class OverlappingFieldsCanBeMerged extends AbstractRule {


    private final List<FieldPair> alreadyChecked = new ArrayList<>();

    public OverlappingFieldsCanBeMerged(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void leaveSelectionSet(SelectionSet selectionSet) {
        Map<String, List<FieldAndType>> fieldMap = new LinkedHashMap<>();
        Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
        collectFields(fieldMap, selectionSet, getValidationContext().getOutputType(), visitedFragmentSpreads);
        List<Conflict> conflicts = findConflicts(fieldMap);
        for (Conflict conflict : conflicts) {
            addError(FieldsConflict, conflict.fields, conflict.reason);
        }

    }


    private List<Conflict> findConflicts(Map<String, List<FieldAndType>> fieldMap) {
        List<Conflict> result = new ArrayList<>();
        for (String name : fieldMap.keySet()) {
            List<FieldAndType> fieldAndTypes = fieldMap.get(name);
            for (int i = 0; i < fieldAndTypes.size(); i++) {
                for (int j = i + 1; j < fieldAndTypes.size(); j++) {
                    Conflict conflict = findConflict(name, fieldAndTypes.get(i), fieldAndTypes.get(j));
                    if (conflict != null) {
                        result.add(conflict);
                    }
                }
            }
        }
        return result;
    }

    private boolean isAlreadyChecked(Field field1, Field field2) {
        for (FieldPair fieldPair : alreadyChecked) {
            if (fieldPair.field1 == field1 && fieldPair.field2 == field2) {
                return true;
            }
            if (fieldPair.field1 == field2 && fieldPair.field2 == field1) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    private Conflict findConflict(String responseName, FieldAndType fieldAndType1, FieldAndType fieldAndType2) {

        Field field1 = fieldAndType1.field;
        Field field2 = fieldAndType2.field;


        GraphQLType type1 = fieldAndType1.graphQLType;
        GraphQLType type2 = fieldAndType2.graphQLType;

        String fieldName1 = field1.getName();
        String fieldName2 = field2.getName();


        if (isAlreadyChecked(field1, field2)) {
            return null;
        }
        alreadyChecked.add(new FieldPair(field1, field2));

        // If the statically known parent types could not possibly apply at the same
        // time, then it is safe to permit them to diverge as they will not present
        // any ambiguity by differing.
        // It is known that two parent types could never overlap if they are
        // different Object types. Interface or Union types might overlap - if not
        // in the current state of the schema, then perhaps in some future version,
        // thus may not safely diverge.
        if (!sameType(fieldAndType1.parentType, fieldAndType1.parentType) &&
                fieldAndType1.parentType instanceof GraphQLObjectType &&
                fieldAndType2.parentType instanceof GraphQLObjectType) {
            return null;
        }

        if (!fieldName1.equals(fieldName2)) {
            String reason = String.format("%s: %s and %s are different fields", responseName, fieldName1, fieldName2);
            return new Conflict(responseName, reason, field1, field2);
        }

        if (!sameType(type1, type2)) {
            String name1 = type1 != null ? type1.getName() : "null";
            String name2 = type2 != null ? type2.getName() : "null";
            String reason = String.format("%s: they return differing types %s and %s", responseName, name1, name2);
            return new Conflict(responseName, reason, field1, field2);
        }


        if (!sameArguments(field1.getArguments(), field2.getArguments())) {
            String reason = String.format("%s: they have differing arguments", responseName);
            return new Conflict(responseName, reason, field1, field2);
        }
        SelectionSet selectionSet1 = field1.getSelectionSet();
        SelectionSet selectionSet2 = field2.getSelectionSet();
        if (selectionSet1 != null && selectionSet2 != null) {
            Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
            Map<String, List<FieldAndType>> subFieldMap = new LinkedHashMap<>();
            collectFields(subFieldMap, selectionSet1, type1, visitedFragmentSpreads);
            collectFields(subFieldMap, selectionSet2, type2, visitedFragmentSpreads);
            List<Conflict> subConflicts = findConflicts(subFieldMap);
            if (subConflicts.size() > 0) {
                String reason = String.format("%s: %s", responseName, joinReasons(subConflicts));
                List<Field> fields = new ArrayList<>();
                fields.add(field1);
                fields.add(field2);
                fields.addAll(collectFields(subConflicts));
                return new Conflict(responseName, reason, fields);
            }
        }

        return null;

    }

    private List<Field> collectFields(List<Conflict> conflicts) {
        List<Field> result = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            result.addAll(conflict.fields);
        }
        return result;
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
        if (type1 == null || type2 == null) return true;
        return type1.equals(type2);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean sameValue(Value value1, Value value2) {
        if (value1 == null && value2 == null) return true;
        if (value1 == null) return false;
        if (value2 == null) return false;
        return new AstComparator().isEqual(value1, value2);
    }

    private boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) return false;
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) return false;
            if (!sameValue(argument.getValue(), matchedArgument.getValue())) return false;
        }
        return true;
    }

    private Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }

    private void collectFields(Map<String, List<FieldAndType>> fieldMap, SelectionSet selectionSet, GraphQLType parentType, Set<String> visitedFragmentSpreads) {

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

    private void collectFieldsForFragmentSpread(Map<String, List<FieldAndType>> fieldMap, Set<String> visitedFragmentSpreads, FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragment == null) return;
        if (visitedFragmentSpreads.contains(fragment.getName())) {
            return;
        }
        visitedFragmentSpreads.add(fragment.getName());
        GraphQLOutputType graphQLType = (GraphQLOutputType) TypeFromAST.getTypeFromAST(getValidationContext().getSchema(),
                fragment.getTypeCondition());
        collectFields(fieldMap, fragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
    }

    private void collectFieldsForInlineFragment(Map<String, List<FieldAndType>> fieldMap, Set<String> visitedFragmentSpreads, GraphQLType parentType, InlineFragment inlineFragment) {
        GraphQLType graphQLType = inlineFragment.getTypeCondition() != null
                ? (GraphQLOutputType) TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), inlineFragment.getTypeCondition())
                : parentType;
        collectFields(fieldMap, inlineFragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
    }

    private void collectFieldsForField(Map<String, List<FieldAndType>> fieldMap, GraphQLType parentType, Field field) {
        String responseName = field.getAlias() != null ? field.getAlias() : field.getName();
        if (!fieldMap.containsKey(responseName)) {
            fieldMap.put(responseName, new ArrayList<>());
        }
        GraphQLOutputType fieldType = null;
        if (parentType instanceof GraphQLFieldsContainer) {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) parentType;
            GraphQLFieldDefinition fieldDefinition = getVisibleFieldDefinition(fieldsContainer, field);
            fieldType = fieldDefinition != null ? fieldDefinition.getType() : null;
        }
        fieldMap.get(responseName).add(new FieldAndType(field, fieldType, parentType));
    }

    private GraphQLFieldDefinition getVisibleFieldDefinition(GraphQLFieldsContainer fieldsContainer, Field field) {
        return getValidationContext().getSchema().getFieldVisibility().getFieldDefinition(fieldsContainer, field.getName());
    }

    private static class FieldPair {
        public FieldPair(Field field1, Field field2) {
            this.field1 = field1;
            this.field2 = field2;
        }

        final Field field1;
        final Field field2;

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


    private static class FieldAndType {
        public FieldAndType(Field field, GraphQLType graphQLType, GraphQLType parentType) {
            this.field = field;
            this.graphQLType = graphQLType;
            this.parentType = parentType;
        }

        final Field field;
        final GraphQLType graphQLType;
        final GraphQLType parentType;
    }
}
