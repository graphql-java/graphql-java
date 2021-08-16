package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.language.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.map;
import static graphql.language.Argument.newArgument;
import static graphql.language.Field.newField;
import static graphql.language.InlineFragment.newInlineFragment;
import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.language.TypeName.newTypeName;

@Internal
public class ExecutableNormalizedOperationToAstCompiler {
    public static Document compileToDocument(
            OperationDefinition.Operation operationKind,
            String operationName,
            List<ExecutableNormalizedField> topLevelFields
    ) {
        List<Selection<?>> selections = selectionsForNormalizedFields(topLevelFields);
        SelectionSet selectionSet = new SelectionSet(selections);

        return Document.newDocument()
                .definition(OperationDefinition.newOperationDefinition()
                        .name(operationName)
                        .operation(operationKind)
                        .selectionSet(selectionSet)
                        .build())
                .build();
    }

    private static List<Selection<?>> selectionsForNormalizedFields(List<ExecutableNormalizedField> executableNormalizedFields) {
        ImmutableList.Builder<Selection<?>> result = ImmutableList.builder();

        Map<String, List<Field>> overallGroupedFields = new LinkedHashMap<>();
        for (ExecutableNormalizedField nf : executableNormalizedFields) {
            Map<String, List<Field>> groupFieldsForChild = selectionForNormalizedField(nf);

            groupFieldsForChild.forEach((objectTypeName, fields) -> {
                List<Field> fieldList = overallGroupedFields.computeIfAbsent(objectTypeName, ignored -> new ArrayList<>());
                fieldList.addAll(fields);
            });

        }

        overallGroupedFields.forEach((objectTypeName, fields) -> {
            TypeName typeName = newTypeName(objectTypeName).build();
            InlineFragment inlineFragment = newInlineFragment().
                    typeCondition(typeName)
                    .selectionSet(selectionSet(fields))
                    .build();
            result.add(inlineFragment);
        });

        return result.build();
    }

    private static Map<String, List<Field>> selectionForNormalizedField(ExecutableNormalizedField executableNormalizedField) {
        Map<String, List<Field>> groupedFields = new LinkedHashMap<>();
        for (String objectTypeName : executableNormalizedField.getObjectTypeNames()) {
            List<Selection<?>> subSelections = selectionsForNormalizedFields(executableNormalizedField.getChildren());
            SelectionSet selectionSet = null;
            if (subSelections.size() > 0) {
                selectionSet = newSelectionSet()
                        .selections(subSelections)
                        .build();
            }
            List<Argument> arguments = createArguments(executableNormalizedField);
            Field field = newField()
                    .name(executableNormalizedField.getFieldName())
                    .alias(executableNormalizedField.getAlias())
                    .selectionSet(selectionSet)
                    .arguments(arguments)
                    .build();

            groupedFields.computeIfAbsent(objectTypeName, ignored -> new ArrayList<>()).add(field);
        }
        return groupedFields;
    }

    private static SelectionSet selectionSet(List<Field> fields) {
        return newSelectionSet().selections(fields).build();
    }

    private static List<Argument> createArguments(ExecutableNormalizedField executableNormalizedField) {
        ImmutableList.Builder<Argument> result = ImmutableList.builder();
        ImmutableMap<String, NormalizedInputValue> normalizedArguments = executableNormalizedField.getNormalizedArguments();
        for (String argName : normalizedArguments.keySet()) {
            Argument argument = newArgument()
                    .name(argName)
                    .value(argValue(normalizedArguments.get(argName).getValue()))
                    .build();
            result.add(argument);
        }
        return result.build();
    }

    private static Value<?> argValue(Object value) {
        if (value instanceof List) {
            ArrayValue.Builder arrayValue = ArrayValue.newArrayValue();
            arrayValue.values(map((List<Object>) value, ExecutableNormalizedOperationToAstCompiler::argValue));
            return arrayValue.build();
        }
        if (value instanceof Map) {
            ObjectValue.Builder objectValue = ObjectValue.newObjectValue();
            Map<String, Object> map = (Map<String, Object>) value;
            for (String fieldName : map.keySet()) {
                Value<?> fieldValue = argValue(((NormalizedInputValue) map.get(fieldName)).getValue());
                objectValue.objectField(ObjectField.newObjectField().name(fieldName).value(fieldValue).build());
            }
            return objectValue.build();
        }
        if (value == null) {
            return NullValue.newNullValue().build();
        }
        return (Value<?>) value;
    }
}
