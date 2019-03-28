package graphql.validation.rules;


import graphql.execution.TypeFromAST;
import graphql.i18n.I18nMsg;
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
import graphql.schema.GraphQLUnmodifiedType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.ArrayList;
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
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.validation.ValidationErrorType.FieldsConflict;
import static java.util.stream.Collectors.joining;

/**
 * See http://facebook.github.io/graphql/June2018/#sec-Field-Selection-Merging
 */
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
            I18nMsg msgAndArgs = conflict.getReason();
            String formattedMessage = i18n(FieldsConflict, msgAndArgs.getMsgKey(), msgAndArgs.getMsgArguments());
            addError(FieldsConflict, conflict.getFields(), formattedMessage);
        }
    }

    private List<Conflict> findConflicts(Map<String, List<FieldAndType>> fieldMap) {
        List<Conflict> result = new ArrayList<>();
        for (String fieldResponseName : fieldMap.keySet()) {
            List<FieldAndType> fieldAndTypes = fieldMap.get(fieldResponseName);
            for (int i = 0; i < fieldAndTypes.size(); i++) {
                for (int j = i + 1; j < fieldAndTypes.size(); j++) {
                    Conflict conflict = findConflict(fieldResponseName, fieldAndTypes.get(i), fieldAndTypes.get(j));
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
    private Conflict findConflict(String fieldResponseName, FieldAndType fieldAndTypeA, FieldAndType fieldAndTypeB) {

        Field fieldA = fieldAndTypeA.field;
        Field fieldB = fieldAndTypeB.field;

        if (isAlreadyChecked(fieldA, fieldB)) {
            return null;
        }
        alreadyChecked.add(new FieldPair(fieldA, fieldB));

        String fieldNameA = fieldA.getName();
        String fieldNameB = fieldB.getName();

        GraphQLType typeA = fieldAndTypeA.graphQLType;
        GraphQLType typeB = fieldAndTypeB.graphQLType;

        Conflict conflict = checkListAndNonNullConflict(fieldResponseName, fieldAndTypeA, fieldAndTypeB);
        if (conflict != null) {
            return conflict;
        }

        typeA = unwrapAll(typeA);
        typeB = unwrapAll(typeB);

        if (checkScalarAndEnumConflict(typeA, typeB)) {
            return mkNotSameTypeError(fieldResponseName, fieldA, fieldB, typeA, typeB);
        }

        // If the statically known parent types could not possibly apply at the same
        // time, then it is safe to permit them to diverge as they will not present
        // any ambiguity by differing.
        // It is known that two parent types could never overlap if they are
        // different Object types. Interface or Union types might overlap - if not
        // in the current state of the schema, then perhaps in some future version,
        // thus may not safely diverge.
        if (!sameType(fieldAndTypeA.parentType, fieldAndTypeB.parentType) &&
                fieldAndTypeA.parentType instanceof GraphQLObjectType &&
                fieldAndTypeB.parentType instanceof GraphQLObjectType) {
            return null;
        }

        if (!fieldNameA.equals(fieldNameB)) {
            I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.differentFields", fieldResponseName, fieldNameA, fieldNameB);
            return new Conflict(reason, fieldA, fieldB);
        }

        if (!sameType(typeA, typeB)) {
            return mkNotSameTypeError(fieldResponseName, fieldA, fieldB, typeA, typeB);
        }


        if (!sameArguments(fieldA.getArguments(), fieldB.getArguments())) {
            I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.differentArgs", fieldResponseName);
            return new Conflict(reason, fieldA, fieldB);
        }
        SelectionSet selectionSet1 = fieldA.getSelectionSet();
        SelectionSet selectionSet2 = fieldB.getSelectionSet();
        if (selectionSet1 != null && selectionSet2 != null) {
            Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
            Map<String, List<FieldAndType>> subFieldMap = new LinkedHashMap<>();
            collectFields(subFieldMap, selectionSet1, typeA, visitedFragmentSpreads);
            collectFields(subFieldMap, selectionSet2, typeB, visitedFragmentSpreads);
            List<Conflict> subConflicts = findConflicts(subFieldMap);
            if (subConflicts.size() > 0) {
                String joinedReasons = joinReasons(subConflicts);
                I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.joinedReasons", fieldResponseName, joinedReasons);
                List<Field> fields = new ArrayList<>();
                fields.add(fieldA);
                fields.add(fieldB);
                fields.addAll(collectFields(subConflicts));
                return new Conflict(reason, fields);
            }
        }
        return null;
    }

    private Conflict checkListAndNonNullConflict(String fieldResponseName, FieldAndType fieldAndTypeA, FieldAndType fieldAndTypeB) {

        GraphQLType typeA = fieldAndTypeA.graphQLType;
        GraphQLType typeB = fieldAndTypeB.graphQLType;

        while (true) {
            if (isNonNull(typeA) || isNonNull(typeB)) {
                if (isNullable(typeA) || isNullable(typeB)) {
                    I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.differentNullability", fieldResponseName);
                    return new Conflict(reason, fieldAndTypeA.field, fieldAndTypeB.field);
                }
            }
            if (isList(typeA) || isList(typeB)) {
                if (!isList(typeA) || !isList(typeB)) {
                    I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.differentLists", fieldResponseName);
                    return new Conflict(reason, fieldAndTypeA.field, fieldAndTypeB.field);
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

    @SuppressWarnings("RedundantIfStatement")
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

    private Conflict mkNotSameTypeError(String fieldResponseName, Field fieldA, Field fieldB, GraphQLType typeA, GraphQLType typeB) {
        String name1 = typeA != null ? typeA.getName() : "null";
        String name2 = typeB != null ? typeB.getName() : "null";
        I18nMsg reason = new I18nMsg("OverlappingFieldsCanBeMerged.differentReturnTypes", fieldResponseName, name1, name2);
        return new Conflict(reason, fieldA, fieldB);
    }

    private List<Field> collectFields(List<Conflict> conflicts) {
        List<Field> result = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            result.addAll(conflict.fields);
        }
        return result;
    }

    private String joinReasons(List<Conflict> conflicts) {
        return conflicts.stream()
                .map(c -> i18n(FieldsConflict, c.getReason()))
                .collect(joining(", ", "(", ")"));
    }

    @SuppressWarnings({"SimplifiableIfStatement", "BooleanMethodIsAlwaysInverted"})
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

    private void collectFieldsForInlineFragment(Map<String, List<FieldAndType>> fieldMap, Set<String> visitedFragmentSpreads, GraphQLType parentType, InlineFragment inlineFragment) {
        GraphQLType graphQLType = inlineFragment.getTypeCondition() != null
                ? TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), inlineFragment.getTypeCondition())
                : parentType;
        collectFields(fieldMap, inlineFragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
    }

    private void collectFieldsForField(Map<String, List<FieldAndType>> fieldMap, GraphQLType parentType, Field field) {
        String responseName = field.getAlias() != null ? field.getAlias() : field.getName();
        if (!fieldMap.containsKey(responseName)) {
            fieldMap.put(responseName, new ArrayList<>());
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

    private static class FieldPair {
        final Field field1;
        final Field field2;

        public FieldPair(Field field1, Field field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    private static class Conflict {
        private final I18nMsg reason;
        private final List<Field> fields = new ArrayList<>();

        public Conflict(I18nMsg reason, Field field1, Field field2) {
            this.reason = reason;
            this.fields.add(field1);
            this.fields.add(field2);
        }

        public Conflict(I18nMsg reason, List<Field> fields) {
            this.reason = reason;
            this.fields.addAll(fields);
        }

        public I18nMsg getReason() {
            return reason;
        }

        public List<Field> getFields() {
            return fields;
        }
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
    }
}
