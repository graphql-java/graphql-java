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
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.newOperationDefinition;
import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.language.TypeName.newTypeName;

@Internal
public class NormalizedQueryToAstCompiler {

    public static Document compileToDocument(List<NormalizedField> topLevelFields) {
        List<Selection<?>> selections = selectionsForNormalizedFields(topLevelFields);
        SelectionSet selectionSet = newSelectionSet(selections).build();
        Document document = Document.newDocument().definition(newOperationDefinition()
                .operation(QUERY)
                .selectionSet(selectionSet)
                .build())
                .build();
        return document;
    }

    private static List<Selection<?>> selectionsForNormalizedFields(List<NormalizedField> normalizedFields) {
        ImmutableList.Builder<Selection<?>> result = ImmutableList.builder();
        for (NormalizedField nf : normalizedFields) {
            result.addAll(selectionForNormalizedField(nf));
        }
        return result.build();
    }

    private static List<Selection<?>> selectionForNormalizedField(NormalizedField normalizedField) {
        List<Selection<?>> result = new ArrayList<>();
        for (String objectType : normalizedField.getObjectTypeNames()) {
            TypeName typeName = newTypeName(objectType).build();
            List<Selection<?>> subSelections = selectionsForNormalizedFields(normalizedField.getChildren());
            SelectionSet selectionSet = null;
            if (subSelections.size() > 0) {
                selectionSet = newSelectionSet()
                        .selections(subSelections)
                        .build();
            }
            List<Argument> arguments = createArguments(normalizedField);
            Field field = newField()
                    .name(normalizedField.getFieldName())
                    .alias(normalizedField.getAlias())
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

    private static List<Argument> createArguments(NormalizedField normalizedField) {
        ImmutableList.Builder<Argument> result = ImmutableList.builder();
        ImmutableMap<String, NormalizedInputValue> normalizedArguments = normalizedField.getNormalizedArguments();
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
            arrayValue.values(map((List<Object>) value, NormalizedQueryToAstCompiler::argValue));
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
