package graphql.validation.rules;


import graphql.execution.TypeFromAST;
import graphql.language.*;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.validation.AbstractRule;
import graphql.validation.ErrorFactory;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.*;

import static graphql.validation.ValidationErrorType.FieldsConflict;

public class OverlappingFieldsCanBeMerged extends AbstractRule {

    ErrorFactory errorFactory = new ErrorFactory();

    public OverlappingFieldsCanBeMerged(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkSelectionSet(SelectionSet selectionSet) {
        Map<String, List<FieldAndType>> fieldMap = new LinkedHashMap<>();
        Set<String> visitedFragmentSpreads = new LinkedHashSet<>();
        collectFields(fieldMap, selectionSet, getValidationContext().getOutputType(), visitedFragmentSpreads);
        List<Conflict> conflicts = findConflicts(fieldMap);
        for (Conflict conflict : conflicts) {
            addError(errorFactory.newError(FieldsConflict, conflict.fields, conflict.reason));
        }

    }

    private List<Conflict> findConflicts(Map<String, List<FieldAndType>> fieldMap) {
        List<Conflict> result = new ArrayList<>();
        for (String name : fieldMap.keySet()) {
            List<FieldAndType> fieldAndTypes = fieldMap.get(name);
            for (int i = 0; i < fieldAndTypes.size(); i++) {
                for (int j = i + i; j < fieldAndTypes.size(); j++) {
                    Conflict conflict = findConflict(name, fieldAndTypes.get(i), fieldAndTypes.get(j));
                    if (conflict != null) {
                        result.add(conflict);
                    }
                }
            }
        }
        return result;
    }

    private Conflict findConflict(String responseName, FieldAndType fieldAndType1, FieldAndType fieldAndType2) {

        Field field1 = fieldAndType1.field;
        Field field2 = fieldAndType2.field;

        String fieldName1 = field1.getName();
        String fieldName2 = field2.getName();
        if (!fieldName1.equals(fieldName2)) {
            String reason = String.format("%s and %s are different fields", fieldName1, fieldName2);
            return new Conflict(responseName, reason, field1, field2);
        }
        return null;

    }


    private void collectFields(Map<String, List<FieldAndType>> fieldMap, SelectionSet selectionSet, GraphQLOutputType parentType, Set<String> visitedFragmentSpreads) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                String responseName = field.getAlias() != null ? field.getAlias() : field.getName();
                if (!fieldMap.containsKey(responseName)) {
                    fieldMap.put(responseName, new ArrayList<FieldAndType>());
                }
                GraphQLOutputType fieldType = null;
                if (parentType instanceof GraphQLFieldsContainer) {
                    GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) parentType;
                    GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(((Field) selection).getName());
                    fieldType = fieldDefinition != null ? fieldDefinition.getType() : null;
                }
                fieldMap.get(responseName).add(new FieldAndType(field, fieldType));

            } else if (selection instanceof InlineFragment) {
                InlineFragment inlineFragment = (InlineFragment) selection;
                GraphQLOutputType graphQLType = (GraphQLOutputType) TypeFromAST.getTypeFromAST(getValidationContext().getSchema(),
                        inlineFragment.getTypeCondition());
                collectFields(fieldMap, inlineFragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);

            } else if (selection instanceof FragmentSpread) {
                FragmentSpread fragmentSpread = (FragmentSpread) selection;
                FragmentDefinition fragment = getValidationContext().getFragment(fragmentSpread.getName());
                if (fragment == null) continue;
                if (visitedFragmentSpreads.contains(fragment.getName())) {
                    continue;
                }
                visitedFragmentSpreads.add(fragment.getName());
                GraphQLOutputType graphQLType = (GraphQLOutputType) TypeFromAST.getTypeFromAST(getValidationContext().getSchema(),
                        fragment.getTypeCondition());
                collectFields(fieldMap, fragment.getSelectionSet(), graphQLType, visitedFragmentSpreads);
            }
        }

    }

    private static class Conflict {
        String responseName;
        String reason;
        List<Field> fields = new ArrayList<>();

        public Conflict(String responseName, String reason, Field field1, Field field2) {
            this.responseName = responseName;
            this.reason = reason;
            this.fields.add(field1);
            this.fields.add(field2);
        }

    }


    private static class FieldAndType {
        public FieldAndType(Field field, GraphQLType graphQLType) {
            this.field = field;
            this.graphQLType = graphQLType;
        }

        Field field;
        GraphQLType graphQLType;
    }
}
