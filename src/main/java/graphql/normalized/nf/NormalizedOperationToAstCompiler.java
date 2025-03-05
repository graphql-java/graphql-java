package graphql.normalized.nf;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnmodifiedType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.emptyList;
import static graphql.language.Field.newField;
import static graphql.language.InlineFragment.newInlineFragment;
import static graphql.language.SelectionSet.newSelectionSet;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * This class can take a list of {@link NormalizedField}s and compiling out a
 * normalised operation {@link Document} that would represent how those fields
 * may be executed.
 * <p>
 * This is essentially the reverse of {@link NormalizedDocumentFactory} which takes
 * operation text and makes {@link NormalizedField}s from it, this takes {@link NormalizedField}s
 * and makes operation text from it.
 * <p>
 * You could for example send that operation text onto to some other graphql server if it
 * has the same schema as the one provided.
 */
@ExperimentalApi
public class NormalizedOperationToAstCompiler {

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

    public static CompilerResult compileToDocument(GraphQLSchema graphQLSchema,
                                                   GraphQLObjectType rootType,
                                                   List<NormalizedField> rootFields,
                                                   @Nullable String operationName,
                                                   OperationDefinition.Operation operationKind) {

        return compileToDocumentImpl(graphQLSchema, rootType, rootFields, operationName, operationKind);
    }

    public static CompilerResult compileToDocument(GraphQLSchema graphQLSchema,
                                                   GraphQLObjectType rootType,
                                                   NormalizedField singleRootField,
                                                   @Nullable String operationName,
                                                   OperationDefinition.Operation operationKind) {
        return compileToDocumentImpl(graphQLSchema, rootType, ImmutableList.of(singleRootField), operationName, operationKind);


    }


    public static CompilerResult compileToDocument(GraphQLSchema schema,
                                                   NormalizedOperation normalizedOperation) {
        GraphQLObjectType operationType = getOperationType(schema, normalizedOperation.getOperation());

        return compileToDocumentImpl(
                schema,
                operationType,
                normalizedOperation.getRootFields(),
                normalizedOperation.getOperationName(),
                normalizedOperation.getOperation()
        );
    }

    private static CompilerResult compileToDocumentImpl(GraphQLSchema schema,
                                                        GraphQLObjectType rootType,
                                                        List<NormalizedField> rootFields,
                                                        @Nullable String operationName,
                                                        OperationDefinition.Operation operationKind) {

        List<Selection<?>> selections = subSelectionsForNormalizedFields(schema, rootType.getName(), rootFields);
        SelectionSet selectionSet = new SelectionSet(selections);

        OperationDefinition.Builder definitionBuilder = OperationDefinition.newOperationDefinition()
                .name(operationName)
                .operation(operationKind)
                .selectionSet(selectionSet);

//        definitionBuilder.variableDefinitions(variableAccumulator.getVariableDefinitions());

        return new CompilerResult(
                Document.newDocument()
                        .definition(definitionBuilder.build())
                        .build(),
                null
        );
    }


    private static List<Selection<?>> subSelectionsForNormalizedFields(GraphQLSchema schema,
                                                                       @NotNull String parentOutputType,
                                                                       List<NormalizedField> normalizedFields
    ) {
        ImmutableList.Builder<Selection<?>> selections = ImmutableList.builder();

        // All conditional fields go here instead of directly to selections, so they can be grouped together
        // in the same inline fragment in the output
        Map<String, List<Field>> fieldsByTypeCondition = new LinkedHashMap<>();

        for (NormalizedField nf : normalizedFields) {
            if (nf.isConditional(schema)) {
                selectionForNormalizedField(schema, nf)
                        .forEach((objectTypeName, field) ->
                                fieldsByTypeCondition
                                        .computeIfAbsent(objectTypeName, ignored -> new ArrayList<>())
                                        .add(field));
            } else {
                selections.add(selectionForNormalizedField(schema, parentOutputType, nf));
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

    /**
     * @return Map of object type names to list of fields
     */
    private static Map<String, Field> selectionForNormalizedField(GraphQLSchema schema,
                                                                  NormalizedField normalizedField
    ) {
        Map<String, Field> groupedFields = new LinkedHashMap<>();

        for (String objectTypeName : normalizedField.getObjectTypeNames()) {
            groupedFields.put(objectTypeName, selectionForNormalizedField(schema, objectTypeName, normalizedField));
        }

        return groupedFields;
    }

    /**
     * @return Map of object type names to list of fields
     */
    private static Field selectionForNormalizedField(GraphQLSchema schema,
                                                     String objectTypeName,
                                                     NormalizedField normalizedField) {

        final List<Selection<?>> subSelections;
        if (normalizedField.getChildren().isEmpty()) {
            subSelections = emptyList();
        } else {
            GraphQLFieldDefinition fieldDef = getFieldDefinition(schema, objectTypeName, normalizedField);
            GraphQLUnmodifiedType fieldOutputType = unwrapAll(fieldDef.getType());

            subSelections = subSelectionsForNormalizedFields(
                    schema,
                    fieldOutputType.getName(),
                    normalizedField.getChildren()
            );
        }

        SelectionSet selectionSet = selectionSetOrNullIfEmpty(subSelections);
//        List<Argument> arguments = createArguments(executableNormalizedField, variableAccumulator);
        List<Argument> arguments = normalizedField.getAstArguments();
        List<Directive> directives = normalizedField.getAstDirectives();


        Field.Builder builder = newField()
                .name(normalizedField.getFieldName())
                .alias(normalizedField.getAlias())
                .selectionSet(selectionSet)
                .directives(directives)
                .arguments(arguments);
        return builder.build();
    }

    @Nullable
    private static SelectionSet selectionSetOrNullIfEmpty(List<Selection<?>> selections) {
        return selections.isEmpty() ? null : newSelectionSet().selections(selections).build();
    }

    private static SelectionSet selectionSet(List<Field> fields) {
        return newSelectionSet().selections(fields).build();
    }


    @NotNull
    private static GraphQLFieldDefinition getFieldDefinition(GraphQLSchema schema,
                                                             String parentType,
                                                             NormalizedField nf) {
        return Introspection.getFieldDef(schema, (GraphQLCompositeType) schema.getType(parentType), nf.getName());
    }


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

}
