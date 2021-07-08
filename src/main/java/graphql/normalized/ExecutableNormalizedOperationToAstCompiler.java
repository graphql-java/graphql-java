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
    public static Document compileToDocument(OperationDefinition.Operation operationKind, List<ExecutableNormalizedField> topLevelFields) {
        List<Selection<?>> selections = selectionsForNormalizedFields(topLevelFields);
        SelectionSet selectionSet = new SelectionSet(selections);

        return Document.newDocument()
                .definition(OperationDefinition.newOperationDefinition()
                        .operation(operationKind)
                        .selectionSet(selectionSet)
                        .build())
                .build();
    }

    private static List<Selection<?>> selectionsForNormalizedFields(List<ExecutableNormalizedField> executableNormalizedFields) {
        ImmutableList.Builder<Selection<?>> result = ImmutableList.builder();
        for (ExecutableNormalizedField nf : executableNormalizedFields) {
            result.addAll(selectionForNormalizedField(nf));
        }
        return result.build();
    }

    private static List<Selection<?>> selectionForNormalizedField(ExecutableNormalizedField executableNormalizedField) {
        List<Selection<?>> result = new ArrayList<>();
        for (String objectType : executableNormalizedField.getObjectTypeNames()) {
            TypeName typeName = newTypeName(objectType).build();
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
            InlineFragment inlineFragment = newInlineFragment().
                    typeCondition(typeName)
                    .selectionSet(selectionSet(field))
                    .build();
            result.add(inlineFragment);
        }
        return result;
    }

    private static SelectionSet selectionSet(Field field) {
        return newSelectionSet().selection(field).build();
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
