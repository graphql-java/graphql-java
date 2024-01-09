package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.PublicApi;
import graphql.execution.directives.QueryDirectives;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.normalized.incremental.DeferExecution;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.map;
import static graphql.language.Argument.newArgument;
import static graphql.language.Field.newField;
import static graphql.language.InlineFragment.newInlineFragment;
import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * This class can take a list of {@link ExecutableNormalizedField}s and compiling out a
 * normalised operation {@link Document} that would represent how those fields
 * may be executed.
 * <p>
 * This is essentially the reverse of {@link ExecutableNormalizedOperationFactory} which takes
 * operation text and makes {@link ExecutableNormalizedField}s from it, this takes {@link ExecutableNormalizedField}s
 * and makes operation text from it.
 * <p>
 * You could for example send that operation text onto to some other graphql server if it
 * has the same schema as the one provided.
 */
@PublicApi
public class ExecutableNormalizedOperationToAstCompiler {

    /**
     * The result is a {@link Document} and a map of variables
     * that would go with that document.
     */
    public static class CompilerResult {
        private final Document document;
        private final Map<String, Object> variables;

        public CompilerResult(Document document, Map<String, Object> variables) {
            this.document = document;
            this.variables = variables;
        }

        public Document getDocument() {
            return document;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }
    }

    /**
     * This will compile an operation text {@link Document} with possibly variables from the given {@link ExecutableNormalizedField}s
     * <p>
     * The {@link VariablePredicate} is used called to decide if the given argument values should be made into a variable
     * OR inlined into the operation text as a graphql literal.
     *
     * @param schema            the graphql schema to use
     * @param operationKind     the kind of operation
     * @param operationName     the name of the operation to use
     * @param topLevelFields    the top level {@link ExecutableNormalizedField}s to start from
     * @param variablePredicate the variable predicate that decides if arguments turn into variables or not during compilation
     *
     * @return a {@link CompilerResult} object
     */
    public static CompilerResult compileToDocument(@NotNull GraphQLSchema schema,
                                                   @NotNull OperationDefinition.Operation operationKind,
                                                   @Nullable String operationName,
                                                   @NotNull List<ExecutableNormalizedField> topLevelFields,
                                                   @Nullable VariablePredicate variablePredicate) {
        return compileToDocument(schema, operationKind, operationName, topLevelFields, Map.of(), variablePredicate);
    }

    /**
     * This will compile an operation text {@link Document} with possibly variables from the given {@link ExecutableNormalizedField}s
     * <p>
     * The {@link VariablePredicate} is used called to decide if the given argument values should be made into a variable
     * OR inlined into the operation text as a graphql literal.
     *
     * @param schema                           the graphql schema to use
     * @param operationKind                    the kind of operation
     * @param operationName                    the name of the operation to use
     * @param topLevelFields                   the top level {@link ExecutableNormalizedField}s to start from
     * @param normalizedFieldToQueryDirectives the map of normalized field to query directives
     * @param variablePredicate                the variable predicate that decides if arguments turn into variables or not during compilation
     *
     * @return a {@link CompilerResult} object
     */
    public static CompilerResult compileToDocument(@NotNull GraphQLSchema schema,
                                                   @NotNull OperationDefinition.Operation operationKind,
                                                   @Nullable String operationName,
                                                   @NotNull List<ExecutableNormalizedField> topLevelFields,
                                                   @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                   @Nullable VariablePredicate variablePredicate) {
        return compileToDocument(schema, operationKind, operationName, topLevelFields, normalizedFieldToQueryDirectives, variablePredicate, false);
    }


    /**
     * This will compile an operation text {@link Document} with possibly variables from the given {@link ExecutableNormalizedField}s, with support for the experimental @defer directive.
     * <p>
     * The {@link VariablePredicate} is used called to decide if the given argument values should be made into a variable
     * OR inlined into the operation text as a graphql literal.
     *
     * @param schema            the graphql schema to use
     * @param operationKind     the kind of operation
     * @param operationName     the name of the operation to use
     * @param topLevelFields    the top level {@link ExecutableNormalizedField}s to start from
     * @param variablePredicate the variable predicate that decides if arguments turn into variables or not during compilation
     *
     * @return a {@link CompilerResult} object
     *
     * @see ExecutableNormalizedOperationToAstCompiler#compileToDocument(GraphQLSchema, OperationDefinition.Operation, String, List, VariablePredicate)
     */
    @ExperimentalApi
    public static CompilerResult compileToDocumentWithDeferSupport(@NotNull GraphQLSchema schema,
                                                                   @NotNull OperationDefinition.Operation operationKind,
                                                                   @Nullable String operationName,
                                                                   @NotNull List<ExecutableNormalizedField> topLevelFields,
                                                                   @Nullable VariablePredicate variablePredicate
    ) {
        return compileToDocumentWithDeferSupport(schema, operationKind, operationName, topLevelFields, Map.of(), variablePredicate);
    }

    /**
     * This will compile an operation text {@link Document} with possibly variables from the given {@link ExecutableNormalizedField}s, with support for the experimental @defer directive.
     * <p>
     * The {@link VariablePredicate} is used called to decide if the given argument values should be made into a variable
     * OR inlined into the operation text as a graphql literal.
     *
     * @param schema                           the graphql schema to use
     * @param operationKind                    the kind of operation
     * @param operationName                    the name of the operation to use
     * @param topLevelFields                   the top level {@link ExecutableNormalizedField}s to start from
     * @param normalizedFieldToQueryDirectives the map of normalized field to query directives
     * @param variablePredicate                the variable predicate that decides if arguments turn into variables or not during compilation
     *
     * @return a {@link CompilerResult} object
     *
     * @see ExecutableNormalizedOperationToAstCompiler#compileToDocument(GraphQLSchema, OperationDefinition.Operation, String, List, Map, VariablePredicate)
     */
    @ExperimentalApi
    public static CompilerResult compileToDocumentWithDeferSupport(@NotNull GraphQLSchema schema,
                                                                   @NotNull OperationDefinition.Operation operationKind,
                                                                   @Nullable String operationName,
                                                                   @NotNull List<ExecutableNormalizedField> topLevelFields,
                                                                   @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                                   @Nullable VariablePredicate variablePredicate
    ) {
        return compileToDocument(schema, operationKind, operationName, topLevelFields, normalizedFieldToQueryDirectives, variablePredicate, true);
    }

    private static CompilerResult compileToDocument(@NotNull GraphQLSchema schema,
                                                    @NotNull OperationDefinition.Operation operationKind,
                                                    @Nullable String operationName,
                                                    @NotNull List<ExecutableNormalizedField> topLevelFields,
                                                    @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                    @Nullable VariablePredicate variablePredicate,
                                                    boolean deferSupport) {
        GraphQLObjectType operationType = getOperationType(schema, operationKind);

        VariableAccumulator variableAccumulator = new VariableAccumulator(variablePredicate);
        List<Selection<?>> selections = subselectionsForNormalizedField(schema, operationType.getName(), topLevelFields, normalizedFieldToQueryDirectives, variableAccumulator, deferSupport);
        SelectionSet selectionSet = new SelectionSet(selections);

        OperationDefinition.Builder definitionBuilder = OperationDefinition.newOperationDefinition()
                .name(operationName)
                .operation(operationKind)
                .selectionSet(selectionSet);

        definitionBuilder.variableDefinitions(variableAccumulator.getVariableDefinitions());

        return new CompilerResult(
                Document.newDocument()
                        .definition(definitionBuilder.build())
                        .build(),
                variableAccumulator.getVariablesMap()
        );
    }

    private static List<Selection<?>> subselectionsForNormalizedField(GraphQLSchema schema,
                                                                      @NotNull String parentOutputType,
                                                                      List<ExecutableNormalizedField> executableNormalizedFields,
                                                                      @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                                      VariableAccumulator variableAccumulator,
                                                                      boolean deferSupport) {
        if (deferSupport) {
            return subselectionsForNormalizedFieldWithDeferSupport(schema, parentOutputType, executableNormalizedFields, normalizedFieldToQueryDirectives, variableAccumulator);
        } else {
            return subselectionsForNormalizedFieldNoDeferSupport(schema, parentOutputType, executableNormalizedFields, normalizedFieldToQueryDirectives, variableAccumulator);
        }
    }

    private static List<Selection<?>> subselectionsForNormalizedFieldNoDeferSupport(GraphQLSchema schema,
                                                                                    @NotNull String parentOutputType,
                                                                                    List<ExecutableNormalizedField> executableNormalizedFields,
                                                                                    @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                                                    VariableAccumulator variableAccumulator) {
        ImmutableList.Builder<Selection<?>> selections = ImmutableList.builder();

        // All conditional fields go here instead of directly to selections, so they can be grouped together
        // in the same inline fragment in the output
        Map<String, List<Field>> fieldsByTypeCondition = new LinkedHashMap<>();

        for (ExecutableNormalizedField nf : executableNormalizedFields) {
            if (nf.isConditional(schema)) {
                selectionForNormalizedField(schema, nf, normalizedFieldToQueryDirectives, variableAccumulator, false)
                        .forEach((objectTypeName, field) ->
                                fieldsByTypeCondition
                                        .computeIfAbsent(objectTypeName, ignored -> new ArrayList<>())
                                        .add(field));
            } else {
                selections.add(selectionForNormalizedField(schema, parentOutputType, nf, normalizedFieldToQueryDirectives, variableAccumulator, false));
            }
        }

        fieldsByTypeCondition.forEach((objectTypeName, fields) -> {
            TypeName typeName = newTypeName(objectTypeName).build();
            InlineFragment inlineFragment = newInlineFragment()
                    .typeCondition(typeName)
                    .selectionSet(selectionSet(fields))
                    .build();
            selections.add(inlineFragment);
        });

        return selections.build();
    }


    private static List<Selection<?>> subselectionsForNormalizedFieldWithDeferSupport(GraphQLSchema schema,
                                                                                      @NotNull String parentOutputType,
                                                                                      List<ExecutableNormalizedField> executableNormalizedFields,
                                                                                      @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                                                      VariableAccumulator variableAccumulator) {
        ImmutableList.Builder<Selection<?>> selections = ImmutableList.builder();

        // All conditional and deferred fields go here instead of directly to selections, so they can be grouped together
        // in the same inline fragment in the output
        //
        Map<ExecutionFragmentDetails, List<Field>> fieldsByFragmentDetails = new LinkedHashMap<>();

        for (ExecutableNormalizedField nf : executableNormalizedFields) {
            LinkedHashSet<DeferExecution> deferExecutions = nf.getDeferExecutions();

            if (nf.isConditional(schema)) {
                selectionForNormalizedField(schema, nf, normalizedFieldToQueryDirectives, variableAccumulator, true)
                        .forEach((objectTypeName, field) -> {
                            if (deferExecutions == null || deferExecutions.isEmpty()) {
                                fieldsByFragmentDetails
                                        .computeIfAbsent(new ExecutionFragmentDetails(objectTypeName, null), ignored -> new ArrayList<>())
                                        .add(field);
                            } else {
                                deferExecutions.forEach(deferExecution -> {
                                    fieldsByFragmentDetails
                                            .computeIfAbsent(new ExecutionFragmentDetails(objectTypeName, deferExecution), ignored -> new ArrayList<>())
                                            .add(field);
                                });
                            }
                        });

            } else if (deferExecutions != null && !deferExecutions.isEmpty()) {
                Field field = selectionForNormalizedField(schema, parentOutputType, nf, normalizedFieldToQueryDirectives, variableAccumulator, true);

                deferExecutions.forEach(deferExecution -> {
                    fieldsByFragmentDetails
                            .computeIfAbsent(new ExecutionFragmentDetails(null, deferExecution), ignored -> new ArrayList<>())
                            .add(field);
                });
            } else {
                selections.add(selectionForNormalizedField(schema, parentOutputType, nf, normalizedFieldToQueryDirectives, variableAccumulator, true));
            }
        }

        fieldsByFragmentDetails.forEach((typeAndDeferPair, fields) -> {
            InlineFragment.Builder fragmentBuilder = newInlineFragment()
                    .selectionSet(selectionSet(fields));

            if (typeAndDeferPair.typeName != null) {
                TypeName typeName = newTypeName(typeAndDeferPair.typeName).build();
                fragmentBuilder.typeCondition(typeName);
            }

            if (typeAndDeferPair.deferExecution != null) {
                Directive.Builder deferBuilder = Directive.newDirective().name(Directives.DeferDirective.getName());

                if (typeAndDeferPair.deferExecution.getLabel() != null) {
                    deferBuilder.argument(newArgument().name("label").value(StringValue.of(typeAndDeferPair.deferExecution.getLabel())).build());
                }

                fragmentBuilder.directive(deferBuilder.build());
            }


            selections.add(fragmentBuilder.build());
        });

        return selections.build();
    }

    /**
     * @return Map of object type names to list of fields
     */
    private static Map<String, Field> selectionForNormalizedField(GraphQLSchema schema,
                                                                  ExecutableNormalizedField executableNormalizedField,
                                                                  @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                                  VariableAccumulator variableAccumulator,
                                                                  boolean deferSupport) {
        Map<String, Field> groupedFields = new LinkedHashMap<>();

        for (String objectTypeName : executableNormalizedField.getObjectTypeNames()) {
            groupedFields.put(objectTypeName, selectionForNormalizedField(schema, objectTypeName, executableNormalizedField, normalizedFieldToQueryDirectives, variableAccumulator, deferSupport));
        }

        return groupedFields;
    }

    /**
     * @return Map of object type names to list of fields
     */
    private static Field selectionForNormalizedField(GraphQLSchema schema,
                                                     String objectTypeName,
                                                     ExecutableNormalizedField executableNormalizedField,
                                                     @NotNull Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
                                                     VariableAccumulator variableAccumulator,
                                                     boolean deferSupport) {
        final List<Selection<?>> subSelections;
        if (executableNormalizedField.getChildren().isEmpty()) {
            subSelections = emptyList();
        } else {
            GraphQLFieldDefinition fieldDef = getFieldDefinition(schema, objectTypeName, executableNormalizedField);
            GraphQLUnmodifiedType fieldOutputType = unwrapAll(fieldDef.getType());

            subSelections = subselectionsForNormalizedField(
                    schema,
                    fieldOutputType.getName(),
                    executableNormalizedField.getChildren(),
                    normalizedFieldToQueryDirectives,
                    variableAccumulator,
                    deferSupport
            );
        }

        SelectionSet selectionSet = selectionSetOrNullIfEmpty(subSelections);
        List<Argument> arguments = createArguments(executableNormalizedField, variableAccumulator);

        QueryDirectives queryDirectives = normalizedFieldToQueryDirectives.get(executableNormalizedField);


        Field.Builder builder = newField()
                .name(executableNormalizedField.getFieldName())
                .alias(executableNormalizedField.getAlias())
                .selectionSet(selectionSet)
                .arguments(arguments);
        if (queryDirectives == null || queryDirectives.getImmediateAppliedDirectivesByField().isEmpty()) {
            return builder.build();
        } else {
            List<Directive> directives = queryDirectives.getImmediateAppliedDirectivesByField().keySet().stream().flatMap(field -> field.getDirectives().stream()).collect(Collectors.toList());
            return builder
                    .directives(directives)
                    .build();
        }
    }

    @Nullable
    private static SelectionSet selectionSetOrNullIfEmpty(List<Selection<?>> selections) {
        return selections.isEmpty() ? null : newSelectionSet().selections(selections).build();
    }

    private static SelectionSet selectionSet(List<Field> fields) {
        return newSelectionSet().selections(fields).build();
    }

    private static List<Argument> createArguments(ExecutableNormalizedField executableNormalizedField,
                                                  VariableAccumulator variableAccumulator) {
        ImmutableList.Builder<Argument> result = ImmutableList.builder();
        ImmutableMap<String, NormalizedInputValue> normalizedArguments = executableNormalizedField.getNormalizedArguments();
        for (String argName : normalizedArguments.keySet()) {
            NormalizedInputValue normalizedInputValue = normalizedArguments.get(argName);
            Value<?> value = argValue(executableNormalizedField, argName, normalizedInputValue, variableAccumulator);
            Argument argument = newArgument()
                    .name(argName)
                    .value(value)
                    .build();
            result.add(argument);
        }
        return result.build();
    }

    @SuppressWarnings("unchecked")
    private static Value<?> argValue(ExecutableNormalizedField executableNormalizedField,
                                     String argName,
                                     @Nullable Object value,
                                     VariableAccumulator variableAccumulator) {
        if (value instanceof List) {
            ArrayValue.Builder arrayValue = ArrayValue.newArrayValue();
            arrayValue.values(map((List<Object>) value, val -> argValue(executableNormalizedField, argName, val, variableAccumulator)));
            return arrayValue.build();
        }
        if (value instanceof Map) {
            ObjectValue.Builder objectValue = ObjectValue.newObjectValue();
            Map<String, Object> map = (Map<String, Object>) value;
            for (String fieldName : map.keySet()) {
                Value<?> fieldValue = argValue(executableNormalizedField, argName, (NormalizedInputValue) map.get(fieldName), variableAccumulator);
                objectValue.objectField(ObjectField.newObjectField().name(fieldName).value(fieldValue).build());
            }
            return objectValue.build();
        }
        if (value == null) {
            return NullValue.newNullValue().build();
        }
        return (Value<?>) value;
    }

    @NotNull
    private static Value<?> argValue(ExecutableNormalizedField executableNormalizedField,
                                     String argName,
                                     NormalizedInputValue normalizedInputValue,
                                     VariableAccumulator variableAccumulator) {
        if (variableAccumulator.shouldMakeVariable(executableNormalizedField, argName, normalizedInputValue)) {
            VariableValueWithDefinition variableWithDefinition = variableAccumulator.accumulateVariable(normalizedInputValue);
            return variableWithDefinition.getVariableReference();
        } else {
            return argValue(executableNormalizedField, argName, normalizedInputValue.getValue(), variableAccumulator);
        }
    }

    @NotNull
    private static GraphQLFieldDefinition getFieldDefinition(GraphQLSchema schema,
                                                             String parentType,
                                                             ExecutableNormalizedField nf) {
        return Introspection.getFieldDef(schema, (GraphQLCompositeType) schema.getType(parentType), nf.getName());
    }


    @Nullable
    private static GraphQLObjectType getOperationType(@NotNull GraphQLSchema schema,
                                                      @NotNull OperationDefinition.Operation operationKind) {
        switch (operationKind) {
            case QUERY:
                return schema.getQueryType();
            case MUTATION:
                return schema.getMutationType();
            case SUBSCRIPTION:
                return schema.getSubscriptionType();
        }

        return Assert.assertShouldNeverHappen("Unknown operation kind " + operationKind);
    }

    /**
     * Represents important execution details that can be associated with a fragment.
     */
    private static class ExecutionFragmentDetails {
        private final String typeName;
        private final DeferExecution deferExecution;

        public ExecutionFragmentDetails(String typeName, DeferExecution deferExecution) {
            this.typeName = typeName;
            this.deferExecution = deferExecution;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ExecutionFragmentDetails that = (ExecutionFragmentDetails) o;
            return Objects.equals(typeName, that.typeName) && Objects.equals(deferExecution, that.deferExecution);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName, deferExecution);
        }
    }
}
