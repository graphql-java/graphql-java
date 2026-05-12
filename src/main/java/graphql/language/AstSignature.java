package graphql.language;

import graphql.AssertException;
import graphql.PublicApi;
import graphql.Scalars;
import graphql.collect.ImmutableKit;
import graphql.execution.CoercedVariables;
import graphql.execution.TypeFromAST;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.util.TreeTransformerUtil.changeNode;

/**
 * This will produce signature and privacy safe query documents that can be used for query categorisation and logging.
 */
@PublicApi
@NullMarked
public class AstSignature {

    /**
     * This can produce a "signature" canonical AST that conforms to the algorithm as outlined
     * <a href="https://github.com/apollographql/apollo-tooling/blob/master/packages/apollo-graphql/src/operationId.ts">here</a>
     * which removes excess operations, removes any field aliases, hides literal values and sorts the result into a canonical
     * query.
     *
     * A signature says two queries with the same signature can be thought of as the same query.  For example
     * a tracing system will want a canonical signature for queries to group them.
     *
     * @param document      the document to make a signature query from
     * @param operationName the name of the operation to do it for (since only one query can be run at a time)
     *
     * @return the signature query in document form
     */
    public Document signatureQuery(Document document, @Nullable String operationName) {
        return sortAST(
                removeAliases(
                        hideLiterals(true,
                                dropUnusedQueryDefinitions(document, operationName)))
        );
    }

    /**
     * This can produce a "privacy safe" AST that some what conforms to the algorithm as outlined
     * <a href="https://github.com/apollographql/apollo-tooling/blob/master/packages/apollo-graphql/src/operationId.ts">here</a>
     * which removes excess operations, removes any field aliases, hides some literal values and sorts the result.
     *
     * This is not a signature.  For example object literal structures are retained like `{ a : "", b : 0}` which means
     * you can infer what was asked for but not what the values are.  This differs from {@link AstSignature#signatureQuery(Document, String)}
     * which collapses literals to create more canonical signatures.  A privacy safe Query is useful for logging say to show
     * what shapes was asked for without revealing what data was provided
     *
     * @param document      the document to make a privacy safe query from
     * @param operationName the name of the operation to do it for (since only one query can be run at a time)
     *
     * @return the privacy safe query in document form
     */
    public Document privacySafeQuery(Document document, @Nullable String operationName) {
        return sortAST(
                removeAliases(
                        hideLiterals(false,
                                dropUnusedQueryDefinitions(document, operationName)))
        );
    }

    /**
     * This can produce a "signature" AST that preserves the shape of arguments and input object fields while redacting
     * all concrete values.  Unlike {@link AstSignature#signatureQuery(Document, String)}, input object fields are retained
     * recursively and variable references are resolved against the provided coerced variable values.
     *
     * Omitted arguments and omitted input object fields stay omitted.  This means two operations with different input
     * shapes can be categorised differently without exposing the values that were supplied.
     * <p>
     * The document's schema references are expected to match the supplied schema.  This method does not attempt to
     * recover from schema mismatches such as unknown fields, arguments, directives, input object fields, variable types
     * or fragment type conditions.  If such a mismatch is encountered, an {@link graphql.AssertException} is thrown
     * immediately.
     *
     * @param document      the document to make a signature query from
     * @param operationName the name of the operation to do it for (since only one query can be run at a time)
     * @param schema        the schema used to resolve field, directive, argument and input object types
     * @param variables     the coerced variables for the operation
     *
     * @return the signature query in document form and the coordinates referenced by it
     */
    public AstSignatureWithInputResult signatureWithInput(Document document, @Nullable String operationName, GraphQLSchema schema, CoercedVariables variables) {
        Map<String, String> variableRemapping = new HashMap<>();
        AtomicInteger variableCount = new AtomicInteger();
        Document wantedDocument = dropUnusedQueryDefinitions(document, operationName);
        AstSignatureReferenceCollector referenceCollector = new AstSignatureReferenceCollector();
        Document signatureDocument = redactInputValues(wantedDocument, operationName, schema, variables, variableRemapping, variableCount, referenceCollector);
        Document sortedDocument = sortExecutableAst(signatureDocument);
        AstSignatureInputReferences references = referenceCollector.toReferences();
        return AstSignatureWithInputResult.newAstSignatureWithInputResult()
                .document(sortedDocument)
                .fieldCoordinates(references.getFieldCoordinates())
                .usedDirectives(references.getUsedDirectives())
                .fieldArgumentCoordinates(references.getFieldArgumentCoordinates())
                .directiveArgumentCoordinates(references.getDirectiveArgumentCoordinates())
                .inputObjectFieldCoordinates(references.getInputObjectFieldCoordinates())
                .build();
    }

    private Document redactInputValues(Document document,
                                       @Nullable String operationName,
                                       GraphQLSchema schema,
                                       CoercedVariables variables,
                                       Map<String, String> variableRemapping,
                                       AtomicInteger variableCount,
                                       AstSignatureReferenceCollector referenceCollector) {
        OperationDefinition operationDefinition = findOperationDefinition(document, operationName);
        Map<String, GraphQLInputType> variableTypes = variableTypes(operationDefinition, schema);
        List<Definition> definitions = new ArrayList<>(document.getDefinitions().size());
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                definitions.add(redactOperationDefinition(
                        (OperationDefinition) definition,
                        schema,
                        variables,
                        variableTypes,
                        variableRemapping,
                        variableCount,
                        referenceCollector.getOperationReferences(),
                        referenceCollector.getOperationFragmentSpreads()
                ));
                continue;
            }
            FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
            definitions.add(redactFragmentDefinition(
                    fragmentDefinition,
                    schema,
                    variables,
                    variableTypes,
                    variableRemapping,
                    variableCount,
                    referenceCollector.getFragmentReferences(fragmentDefinition.getName()),
                    referenceCollector.getFragmentSpreads(fragmentDefinition.getName())
            ));
        }
        return document.transform(builder -> builder.definitions(definitions));
    }

    private OperationDefinition redactOperationDefinition(OperationDefinition operationDefinition,
                                                         GraphQLSchema schema,
                                                         CoercedVariables variables,
                                                         Map<String, GraphQLInputType> variableTypes,
                                                         Map<String, String> variableRemapping,
                                                         AtomicInteger variableCount,
                                                         AstSignatureInputReferences references,
                                                         Set<String> fragmentSpreads) {
        GraphQLOutputType rootType = operationRootType(operationDefinition, schema);
        return operationDefinition.transform(builder -> {
            builder.variableDefinitions(redactVariableDefinitions(operationDefinition.getVariableDefinitions(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.directives(redactDirectives(operationDefinition.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.selectionSet(redactSelectionSet(operationDefinition.getSelectionSet(), rootType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads));
        });
    }

    private FragmentDefinition redactFragmentDefinition(FragmentDefinition fragmentDefinition,
                                                       GraphQLSchema schema,
                                                       CoercedVariables variables,
                                                       Map<String, GraphQLInputType> variableTypes,
                                                       Map<String, String> variableRemapping,
                                                       AtomicInteger variableCount,
                                                       AstSignatureInputReferences references,
                                                       Set<String> fragmentSpreads) {
        TypeName typeCondition = fragmentDefinition.getTypeCondition();
        GraphQLOutputType outputType = outputType(schema, typeCondition);
        if (outputType == null) {
            throw schemaMismatch("fragment type condition '%s' must be present in the schema as an output type", typeCondition.getName());
        }
        return fragmentDefinition.transform(builder -> {
            builder.directives(redactDirectives(fragmentDefinition.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.selectionSet(redactSelectionSet(fragmentDefinition.getSelectionSet(), outputType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads));
        });
    }

    private List<VariableDefinition> redactVariableDefinitions(List<VariableDefinition> variableDefinitions,
                                                               GraphQLSchema schema,
                                                               CoercedVariables variables,
                                                               Map<String, GraphQLInputType> variableTypes,
                                                               Map<String, String> variableRemapping,
                                                               AtomicInteger variableCount,
                                                               AstSignatureInputReferences references) {
        List<VariableDefinition> result = new ArrayList<>(variableDefinitions.size());
        for (VariableDefinition variableDefinition : variableDefinitions) {
            result.add(redactVariableDefinition(variableDefinition, schema, variables, variableTypes, variableRemapping, variableCount, references));
        }
        return result;
    }

    private VariableDefinition redactVariableDefinition(VariableDefinition variableDefinition,
                                                       GraphQLSchema schema,
                                                       CoercedVariables variables,
                                                       Map<String, GraphQLInputType> variableTypes,
                                                       Map<String, String> variableRemapping,
                                                       AtomicInteger variableCount,
                                                       AstSignatureInputReferences references) {
        Value defaultValue = variableDefinition.getDefaultValue();
        GraphQLInputType variableType = variableTypes.get(variableDefinition.getName());
        Value redactedDefaultValue = defaultValue == null || variableType == null
                ? defaultValue
                : redactValue(defaultValue, variableType, schema, variables, variableRemapping, variableCount, references);
        return variableDefinition.transform(builder -> {
            builder.name(remapVariable(variableDefinition.getName(), variableRemapping, variableCount));
            builder.defaultValue(redactedDefaultValue);
            builder.directives(redactDirectives(variableDefinition.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
        });
    }

    private SelectionSet redactSelectionSet(SelectionSet selectionSet,
                                            GraphQLOutputType parentType,
                                            GraphQLSchema schema,
                                            CoercedVariables variables,
                                            Map<String, GraphQLInputType> variableTypes,
                                            Map<String, String> variableRemapping,
                                            AtomicInteger variableCount,
                                            AstSignatureInputReferences references,
                                            Set<String> fragmentSpreads) {
        List<Selection> selections = new ArrayList<>(selectionSet.getSelections().size());
        for (Selection selection : selectionSet.getSelections()) {
            selections.add(redactSelection(selection, parentType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads));
        }
        return selectionSet.transform(builder -> builder.selections(selections));
    }

    private Selection redactSelection(Selection selection,
                                      GraphQLOutputType parentType,
                                      GraphQLSchema schema,
                                      CoercedVariables variables,
                                      Map<String, GraphQLInputType> variableTypes,
                                      Map<String, String> variableRemapping,
                                      AtomicInteger variableCount,
                                      AstSignatureInputReferences references,
                                      Set<String> fragmentSpreads) {
        if (selection instanceof Field) {
            return redactField((Field) selection, parentType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads);
        }
        if (selection instanceof InlineFragment) {
            return redactInlineFragment((InlineFragment) selection, parentType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads);
        }
        return redactFragmentSpread((FragmentSpread) selection, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads);
    }

    private Field redactField(Field field,
                              GraphQLOutputType parentType,
                              GraphQLSchema schema,
                              CoercedVariables variables,
                              Map<String, GraphQLInputType> variableTypes,
                              Map<String, String> variableRemapping,
                              AtomicInteger variableCount,
                              AstSignatureInputReferences references,
                              Set<String> fragmentSpreads) {
        GraphQLCompositeType compositeType = (GraphQLCompositeType) unwrapAll(parentType);
        GraphQLFieldDefinition fieldDefinition = fieldDefinition(schema, compositeType, field.getName());
        if (fieldDefinition == null) {
            throw schemaMismatch("field '%s.%s' must be present in the schema", compositeType.getName(), field.getName());
        }
        return field.transform(builder -> {
            String fieldCoordinate = fieldCoordinate(compositeType, field);
            if (fieldCoordinate != null) {
                references.addFieldCoordinate(fieldCoordinate);
            }
            builder.alias(null);
            builder.arguments(redactFieldArguments(fieldCoordinate, field.getArguments(), fieldDefinition.getArguments(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.directives(redactDirectives(field.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.selectionSet(redactFieldSelectionSet(field, fieldDefinition.getType(), schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads));
        });
    }

    private @Nullable GraphQLFieldDefinition fieldDefinition(GraphQLSchema schema, GraphQLCompositeType compositeType, String fieldName) {
        GraphQLFieldDefinition systemFieldDefinition = systemFieldDefinition(schema, compositeType, fieldName);
        if (systemFieldDefinition != null) {
            return systemFieldDefinition;
        }
        if (!(compositeType instanceof GraphQLFieldsContainer)) {
            return null;
        }
        GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) compositeType;
        return schema.getCodeRegistry().getFieldVisibility().getFieldDefinition(fieldsContainer, fieldName);
    }

    private @Nullable GraphQLFieldDefinition systemFieldDefinition(GraphQLSchema schema, GraphQLCompositeType compositeType, String fieldName) {
        if (fieldName.equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
            return schema.getIntrospectionTypenameFieldDefinition();
        }
        if (schema.getQueryType() != compositeType) {
            return null;
        }
        if (fieldName.equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
            return schema.getIntrospectionSchemaFieldDefinition();
        }
        if (fieldName.equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
            return schema.getIntrospectionTypeFieldDefinition();
        }
        return null;
    }

    private @Nullable String fieldCoordinate(GraphQLCompositeType compositeType, Field field) {
        if (field.getName().startsWith("__")) {
            return null;
        }
        GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) compositeType;
        return fieldsContainer.getName() + "." + field.getName();
    }

    private @Nullable SelectionSet redactFieldSelectionSet(Field field,
                                                           GraphQLOutputType fieldType,
                                                           GraphQLSchema schema,
                                                           CoercedVariables variables,
                                                           Map<String, GraphQLInputType> variableTypes,
                                                           Map<String, String> variableRemapping,
                                                           AtomicInteger variableCount,
                                                           AstSignatureInputReferences references,
                                                           Set<String> fragmentSpreads) {
        SelectionSet selectionSet = field.getSelectionSet();
        if (selectionSet == null) {
            return null;
        }
        GraphQLOutputType unmodifiedType = (GraphQLOutputType) unwrapAll(fieldType);
        return redactSelectionSet(selectionSet, unmodifiedType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads);
    }

    private InlineFragment redactInlineFragment(InlineFragment inlineFragment,
                                                GraphQLOutputType parentType,
                                                GraphQLSchema schema,
                                                CoercedVariables variables,
                                                Map<String, GraphQLInputType> variableTypes,
                                                Map<String, String> variableRemapping,
                                                AtomicInteger variableCount,
                                                AstSignatureInputReferences references,
                                                Set<String> fragmentSpreads) {
        TypeName typeCondition = inlineFragment.getTypeCondition();
        GraphQLOutputType outputType = inlineFragmentOutputType(parentType, schema, typeCondition);
        return inlineFragment.transform(builder -> {
            builder.directives(redactDirectives(inlineFragment.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
            builder.selectionSet(redactSelectionSet(inlineFragment.getSelectionSet(), outputType, schema, variables, variableTypes, variableRemapping, variableCount, references, fragmentSpreads));
        });
    }

    private GraphQLOutputType inlineFragmentOutputType(GraphQLOutputType parentType, GraphQLSchema schema, @Nullable TypeName typeCondition) {
        if (typeCondition == null) {
            return parentType;
        }
        GraphQLOutputType outputType = outputType(schema, typeCondition);
        if (outputType != null) {
            return outputType;
        }
        throw schemaMismatch("inline fragment type condition '%s' must be present in the schema as an output type", typeCondition.getName());
    }

    private FragmentSpread redactFragmentSpread(FragmentSpread fragmentSpread,
                                                GraphQLSchema schema,
                                                CoercedVariables variables,
                                                Map<String, GraphQLInputType> variableTypes,
                                                Map<String, String> variableRemapping,
                                                AtomicInteger variableCount,
                                                AstSignatureInputReferences references,
                                                Set<String> fragmentSpreads) {
        fragmentSpreads.add(fragmentSpread.getName());
        return fragmentSpread.transform(builder -> {
            builder.directives(redactDirectives(fragmentSpread.getDirectives(), schema, variables, variableTypes, variableRemapping, variableCount, references));
        });
    }

    private List<Directive> redactDirectives(List<Directive> directives,
                                             GraphQLSchema schema,
                                             CoercedVariables variables,
                                             Map<String, GraphQLInputType> variableTypes,
                                             Map<String, String> variableRemapping,
                                             AtomicInteger variableCount,
                                             AstSignatureInputReferences references) {
        List<Directive> result = new ArrayList<>(directives.size());
        for (Directive directive : directives) {
            GraphQLDirective directiveDefinition = schema.getDirective(directive.getName());
            result.add(redactDirective(directive, directiveDefinition, schema, variables, variableTypes, variableRemapping, variableCount, references));
        }
        return result;
    }

    private Directive redactDirective(Directive directive,
                                      @Nullable GraphQLDirective directiveDefinition,
                                      GraphQLSchema schema,
                                      CoercedVariables variables,
                                      Map<String, GraphQLInputType> variableTypes,
                                      Map<String, String> variableRemapping,
                                      AtomicInteger variableCount,
                                      AstSignatureInputReferences references) {
        if (directiveDefinition == null) {
            throw schemaMismatch("directive '@%s' must be present in the schema", directive.getName());
        }
        String usedDirective = "@" + directive.getName();
        references.addUsedDirective(usedDirective);
        return directive.transform(builder -> {
            builder.arguments(redactDirectiveArguments(usedDirective, directive.getArguments(), directiveDefinition.getArguments(), schema, variables, variableTypes, variableRemapping, variableCount, references));
        });
    }

    private List<Argument> redactFieldArguments(@Nullable String fieldCoordinate,
                                                List<Argument> arguments,
                                                List<GraphQLArgument> argumentDefinitions,
                                                GraphQLSchema schema,
                                                CoercedVariables variables,
                                                Map<String, GraphQLInputType> variableTypes,
                                                Map<String, String> variableRemapping,
                                                AtomicInteger variableCount,
                                                AstSignatureInputReferences references) {
        return redactArguments(
                fieldCoordinate,
                null,
                arguments,
                argumentDefinitions,
                schema,
                variables,
                variableTypes,
                variableRemapping,
                variableCount,
                references
        );
    }

    private List<Argument> redactDirectiveArguments(String usedDirective,
                                                    List<Argument> arguments,
                                                    List<GraphQLArgument> argumentDefinitions,
                                                    GraphQLSchema schema,
                                                    CoercedVariables variables,
                                                    Map<String, GraphQLInputType> variableTypes,
                                                    Map<String, String> variableRemapping,
                                                    AtomicInteger variableCount,
                                                    AstSignatureInputReferences references) {
        return redactArguments(
                null,
                usedDirective,
                arguments,
                argumentDefinitions,
                schema,
                variables,
                variableTypes,
                variableRemapping,
                variableCount,
                references
        );
    }

    private List<Argument> redactArguments(@Nullable String fieldCoordinate,
                                           @Nullable String usedDirective,
                                           List<Argument> arguments,
                                           List<GraphQLArgument> argumentDefinitions,
                                           GraphQLSchema schema,
                                           CoercedVariables variables,
                                           Map<String, GraphQLInputType> variableTypes,
                                           Map<String, String> variableRemapping,
                                           AtomicInteger variableCount,
                                           AstSignatureInputReferences references) {
        Map<String, GraphQLArgument> argumentDefinitionByName = argumentDefinitionByName(argumentDefinitions);
        List<Argument> result = new ArrayList<>(arguments.size());
        for (Argument argument : arguments) {
            GraphQLArgument argumentDefinition = argumentDefinitionByName.get(argument.getName());
            if (argumentDefinition == null) {
                throw schemaMismatch("argument '%s' must be present in the schema", argument.getName());
            }
            Argument redactedArgument = redactArgument(argument, argumentDefinition, schema, variables, variableTypes, variableRemapping, variableCount, references);
            if (redactedArgument != null) {
                collectArgumentReference(fieldCoordinate, usedDirective, redactedArgument, references);
                result.add(redactedArgument);
            }
        }
        return result;
    }

    private void collectArgumentReference(@Nullable String fieldCoordinate,
                                          @Nullable String usedDirective,
                                          Argument argument,
                                          AstSignatureInputReferences references) {
        if (fieldCoordinate != null) {
            references.addFieldArgumentCoordinate(fieldCoordinate + "(" + argument.getName() + ":)");
        }
        if (usedDirective != null) {
            references.addDirectiveArgumentCoordinate(usedDirective + "(" + argument.getName() + ":)");
        }
    }

    private @Nullable Argument redactArgument(Argument argument,
                                              GraphQLArgument argumentDefinition,
                                              GraphQLSchema schema,
                                              CoercedVariables variables,
                                              Map<String, GraphQLInputType> variableTypes,
                                              Map<String, String> variableRemapping,
                                              AtomicInteger variableCount,
                                              AstSignatureInputReferences references) {
        if (isAbsentVariableReference(argument.getValue(), variables)) {
            return null;
        }
        Value redactedValue = redactValue(argument.getValue(), argumentDefinition.getType(), schema, variables, variableRemapping, variableCount, references);
        return argument.transform(builder -> builder.value(redactedValue));
    }

    private Value redactValue(Value value,
                              GraphQLInputType inputType,
                              GraphQLSchema schema,
                              CoercedVariables variables,
                              Map<String, String> variableRemapping,
                              AtomicInteger variableCount,
                              AstSignatureInputReferences references) {
        if (value instanceof VariableReference) {
            return redactVariableReference((VariableReference) value, inputType, schema, variables, references);
        }
        if (value instanceof NullValue) {
            return NullValue.of();
        }
        if (isNonNull(inputType)) {
            return redactValue(value, (GraphQLInputType) unwrapOne(inputType), schema, variables, variableRemapping, variableCount, references);
        }
        if (isList(inputType)) {
            return redactListValue(value, (GraphQLList) inputType, schema, variables, variableRemapping, variableCount, references);
        }
        if (inputType instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) inputType;
            if (value instanceof ObjectValue) {
                return redactObjectValue((ObjectValue) value, inputObjectType, schema, variables, variableRemapping, variableCount, references);
            }
            throw schemaMismatch("input value for type '%s' must be an object", inputObjectType.getName());
        }
        if (inputType instanceof GraphQLEnumType) {
            return EnumValue.of("REDACTED");
        }
        assertTrue(inputType instanceof GraphQLScalarType, "input type '%s' must be a scalar, enum, input object, list or non-null type", inputType);
        return redactedScalarValue((GraphQLScalarType) inputType);
    }

    private Value redactVariableReference(VariableReference variableReference,
                                          GraphQLInputType inputType,
                                          GraphQLSchema schema,
                                          CoercedVariables variables,
                                          AstSignatureInputReferences references) {
        if (!variables.containsKey(variableReference.getName())) {
            return NullValue.of();
        }
        return redactExternalValue(variables.get(variableReference.getName()), inputType, schema, references);
    }

    private Value redactListValue(Value value,
                                  GraphQLList listType,
                                  GraphQLSchema schema,
                                  CoercedVariables variables,
                                  Map<String, String> variableRemapping,
                                  AtomicInteger variableCount,
                                  AstSignatureInputReferences references) {
        GraphQLInputType wrappedType = (GraphQLInputType) listType.getWrappedType();
        if (!(value instanceof ArrayValue)) {
            return redactValue(value, wrappedType, schema, variables, variableRemapping, variableCount, references);
        }
        ArrayValue arrayValue = (ArrayValue) value;
        List<Value> values = new ArrayList<>(arrayValue.getValues().size());
        for (Value item : arrayValue.getValues()) {
            values.add(redactValue(item, wrappedType, schema, variables, variableRemapping, variableCount, references));
        }
        return arrayValue.transform(builder -> builder.values(values));
    }

    private ObjectValue redactObjectValue(ObjectValue objectValue,
                                          GraphQLInputObjectType inputObjectType,
                                          GraphQLSchema schema,
                                          CoercedVariables variables,
                                          Map<String, String> variableRemapping,
                                          AtomicInteger variableCount,
                                          AstSignatureInputReferences references) {
        Map<String, GraphQLInputObjectField> fieldDefinitionByName = inputObjectFieldDefinitionByName(inputObjectType, schema);
        List<ObjectField> objectFields = new ArrayList<>(objectValue.getObjectFields().size());
        for (ObjectField objectField : objectValue.getObjectFields()) {
            ObjectField redactedObjectField = redactObjectField(objectField, inputObjectType, fieldDefinitionByName.get(objectField.getName()), schema, variables, variableRemapping, variableCount, references);
            if (redactedObjectField != null) {
                objectFields.add(redactedObjectField);
            }
        }
        return objectValue.transform(builder -> builder.objectFields(objectFields));
    }

    private @Nullable ObjectField redactObjectField(ObjectField objectField,
                                                    GraphQLInputObjectType inputObjectType,
                                                    @Nullable GraphQLInputObjectField fieldDefinition,
                                                    GraphQLSchema schema,
                                                    CoercedVariables variables,
                                                    Map<String, String> variableRemapping,
                                                    AtomicInteger variableCount,
                                                    AstSignatureInputReferences references) {
        if (isAbsentVariableReference(objectField.getValue(), variables)) {
            return null;
        }
        if (fieldDefinition == null) {
            throw schemaMismatch("input object field '%s.%s' must be present in the schema", inputObjectType.getName(), objectField.getName());
        }
        references.addInputObjectFieldCoordinate(inputObjectType.getName() + "." + objectField.getName());
        Value redactedValue = redactValue(objectField.getValue(), fieldDefinition.getType(), schema, variables, variableRemapping, variableCount, references);
        return objectField.transform(builder -> builder.value(redactedValue));
    }

    private Value redactExternalValue(@Nullable Object value,
                                      GraphQLInputType inputType,
                                      GraphQLSchema schema,
                                      AstSignatureInputReferences references) {
        if (value == null) {
            return NullValue.of();
        }
        if (isNonNull(inputType)) {
            return redactExternalValue(value, (GraphQLInputType) unwrapOne(inputType), schema, references);
        }
        if (inputType instanceof GraphQLList) {
            return redactExternalListValue(value, (GraphQLList) inputType, schema, references);
        }
        if (inputType instanceof GraphQLInputObjectType) {
            return redactExternalObjectValue(value, (GraphQLInputObjectType) inputType, schema, references);
        }
        if (inputType instanceof GraphQLEnumType) {
            return EnumValue.of("REDACTED");
        }
        GraphQLScalarType scalarType = Scalars.GraphQLString;
        if (inputType instanceof GraphQLScalarType) {
            scalarType = (GraphQLScalarType) inputType;
        }
        return redactedScalarValue(scalarType);
    }

    private ArrayValue redactExternalListValue(Object value,
                                               GraphQLList listType,
                                               GraphQLSchema schema,
                                               AstSignatureInputReferences references) {
        GraphQLInputType wrappedType = (GraphQLInputType) listType.getWrappedType();
        List<?> values = value instanceof List<?> ? (List<?>) value : Collections.singletonList(value);
        List<Value> redactedValues = new ArrayList<>(values.size());
        for (Object item : values) {
            redactedValues.add(redactExternalValue(item, wrappedType, schema, references));
        }
        return ArrayValue.newArrayValue().values(redactedValues).build();
    }

    private Value redactExternalObjectValue(Object value,
                                            GraphQLInputObjectType inputObjectType,
                                            GraphQLSchema schema,
                                            AstSignatureInputReferences references) {
        if (!(value instanceof Map<?, ?>)) {
            return ObjectValue.newObjectValue().build();
        }
        Map<?, ?> inputMap = (Map<?, ?>) value;
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        List<ObjectField> objectFields = new ArrayList<>();
        for (GraphQLInputObjectField fieldDefinition : fieldVisibility.getFieldDefinitions(inputObjectType)) {
            String fieldName = fieldDefinition.getName();
            if (inputMap.containsKey(fieldName)) {
                references.addInputObjectFieldCoordinate(inputObjectType.getName() + "." + fieldName);
                Value redactedValue = redactExternalValue(inputMap.get(fieldName), fieldDefinition.getType(), schema, references);
                objectFields.add(ObjectField.newObjectField().name(fieldName).value(redactedValue).build());
            }
        }
        return ObjectValue.newObjectValue().objectFields(objectFields).build();
    }

    private Value redactedScalarValue(GraphQLScalarType scalarType) {
        if (Scalars.GraphQLInt.getName().equals(scalarType.getName())) {
            return IntValue.of(0);
        }
        if (Scalars.GraphQLFloat.getName().equals(scalarType.getName())) {
            return FloatValue.newFloatValue(BigDecimal.ZERO).build();
        }
        if (Scalars.GraphQLBoolean.getName().equals(scalarType.getName())) {
            return BooleanValue.of(false);
        }
        return StringValue.of("");
    }

    private boolean isAbsentVariableReference(Value value, CoercedVariables variables) {
        if (!(value instanceof VariableReference)) {
            return false;
        }
        VariableReference variableReference = (VariableReference) value;
        return !variables.containsKey(variableReference.getName());
    }

    private Map<String, GraphQLArgument> argumentDefinitionByName(List<GraphQLArgument> argumentDefinitions) {
        Map<String, GraphQLArgument> result = new HashMap<>();
        for (GraphQLArgument argumentDefinition : argumentDefinitions) {
            result.put(argumentDefinition.getName(), argumentDefinition);
        }
        return result;
    }

    private Map<String, GraphQLInputObjectField> inputObjectFieldDefinitionByName(GraphQLInputObjectType inputObjectType, GraphQLSchema schema) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, GraphQLInputObjectField> result = new HashMap<>();
        for (GraphQLInputObjectField fieldDefinition : fieldVisibility.getFieldDefinitions(inputObjectType)) {
            result.put(fieldDefinition.getName(), fieldDefinition);
        }
        return result;
    }

    private Map<String, GraphQLInputType> variableTypes(@Nullable OperationDefinition operationDefinition, GraphQLSchema schema) {
        if (operationDefinition == null) {
            return Collections.emptyMap();
        }
        Map<String, GraphQLInputType> result = new HashMap<>();
        for (VariableDefinition variableDefinition : operationDefinition.getVariableDefinitions()) {
            GraphQLType graphQLType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            if (!(graphQLType instanceof GraphQLInputType)) {
                throw schemaMismatch("variable type '%s' must be present in the schema as an input type", variableDefinition.getType());
            }
            result.put(variableDefinition.getName(), (GraphQLInputType) graphQLType);
        }
        return result;
    }

    private AssertException schemaMismatch(String msgFmt, Object... args) {
        return new AssertException(String.format(msgFmt, args));
    }

    private @Nullable OperationDefinition findOperationDefinition(Document document, @Nullable String operationName) {
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition && isThisOperation((OperationDefinition) definition, operationName)) {
                return (OperationDefinition) definition;
            }
        }
        return null;
    }

    private GraphQLOutputType operationRootType(OperationDefinition operationDefinition, GraphQLSchema schema) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return assertNotNull(schema.getMutationType(), "mutation root type must be present");
        }
        if (operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            return assertNotNull(schema.getSubscriptionType(), "subscription root type must be present");
        }
        GraphQLObjectType queryType = schema.getQueryType();
        return assertNotNull(queryType, "query root type must be present");
    }

    private @Nullable GraphQLOutputType outputType(GraphQLSchema schema, TypeName typeName) {
        GraphQLType graphQLType = schema.getType(typeName.getName());
        return graphQLType instanceof GraphQLOutputType ? (GraphQLOutputType) graphQLType : null;
    }

    private Document hideLiterals(boolean signatureMode, Document document) {
        final Map<String, String> variableRemapping = new HashMap<>();
        final AtomicInteger variableCount = new AtomicInteger();

        NodeVisitorStub visitor = new NodeVisitorStub() {
            @Override
            public TraversalControl visitIntValue(IntValue node, TraverserContext<Node> context) {
                return changeNode(context, node.transform(builder -> builder.value(BigInteger.ZERO)));
            }

            @Override
            public TraversalControl visitFloatValue(FloatValue node, TraverserContext<Node> context) {
                return changeNode(context, node.transform(builder -> builder.value(BigDecimal.ZERO)));
            }

            @Override
            public TraversalControl visitStringValue(StringValue node, TraverserContext<Node> context) {
                return changeNode(context, node.transform(builder -> builder.value("")));
            }

            @Override
            public TraversalControl visitBooleanValue(BooleanValue node, TraverserContext<Node> context) {
                return changeNode(context, node.transform(builder -> builder.value(false)));
            }

            @Override
            public TraversalControl visitArrayValue(ArrayValue node, TraverserContext<Node> context) {
                if (signatureMode) {
                    return changeNode(context, node.transform(builder -> builder.values(ImmutableKit.emptyList())));
                }
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl visitObjectValue(ObjectValue node, TraverserContext<Node> context) {
                if (signatureMode) {
                    return changeNode(context, node.transform(builder -> builder.objectFields(ImmutableKit.emptyList())));
                }
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl visitVariableReference(VariableReference node, TraverserContext<Node> context) {
                String varName = remapVariable(node.getName(), variableRemapping, variableCount);
                return changeNode(context, node.transform(builder -> builder.name(varName)));
            }

            @Override
            public TraversalControl visitVariableDefinition(VariableDefinition node, TraverserContext<Node> context) {
                String varName = remapVariable(node.getName(), variableRemapping, variableCount);
                return changeNode(context, node.transform(builder -> builder.name(varName)));
            }
        };
        return transformDoc(document, visitor);
    }

    private String remapVariable(String varName, Map<String, String> variableRemapping, AtomicInteger variableCount) {
        String mappedName = variableRemapping.get(varName);
        if (mappedName == null) {
            mappedName = "var" + variableCount.incrementAndGet();
            variableRemapping.put(varName, mappedName);
        }
        return mappedName;
    }

    private Document removeAliases(Document document) {
        NodeVisitorStub visitor = new NodeVisitorStub() {
            @Override
            public TraversalControl visitField(Field node, TraverserContext<Node> context) {
                return changeNode(context, node.transform(builder -> builder.alias(null)));
            }
        };
        return transformDoc(document, visitor);
    }

    private Document sortAST(Document document) {
        return new AstSorter().sort(document);
    }

    // signatureWithInput has already pruned the document to at most one operation plus fragments.
    // Avoid the generic AstSorter here because it pays AstTransformer visitor overhead and supports
    // SDL/node kinds that cannot be present on this hot path.
    private Document sortExecutableAst(Document document) {
        List<Definition> definitions = new ArrayList<>(document.getDefinitions().size());
        List<FragmentDefinition> fragments = new ArrayList<>();
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition) {
                fragments.add(sortFragmentDefinition((FragmentDefinition) definition));
                continue;
            }
            definitions.add(sortOperationDefinition((OperationDefinition) definition));
        }
        fragments.sort((left, right) -> left.getName().compareTo(right.getName()));
        definitions.addAll(fragments);
        return document.transform(builder -> builder.definitions(definitions));
    }

    private OperationDefinition sortOperationDefinition(OperationDefinition operationDefinition) {
        return operationDefinition.transform(builder -> {
            builder.variableDefinitions(sortVariableDefinitions(operationDefinition.getVariableDefinitions()));
            builder.directives(sortDirectives(operationDefinition.getDirectives()));
            builder.selectionSet(sortSelectionSet(operationDefinition.getSelectionSet()));
        });
    }

    private FragmentDefinition sortFragmentDefinition(FragmentDefinition fragmentDefinition) {
        return fragmentDefinition.transform(builder -> {
            builder.directives(sortDirectives(fragmentDefinition.getDirectives()));
            builder.selectionSet(sortSelectionSet(fragmentDefinition.getSelectionSet()));
        });
    }

    private SelectionSet sortSelectionSet(SelectionSet selectionSet) {
        List<Selection> selections = new ArrayList<>(selectionSet.getSelections().size());
        for (Selection selection : selectionSet.getSelections()) {
            selections.add(sortSelection(selection));
        }
        selections.sort(this::compareSelections);
        return selectionSet.transform(builder -> builder.selections(selections));
    }

    private Selection sortSelection(Selection selection) {
        if (selection instanceof Field) {
            return sortField((Field) selection);
        }
        if (selection instanceof InlineFragment) {
            return sortInlineFragment((InlineFragment) selection);
        }
        return sortFragmentSpread((FragmentSpread) selection);
    }

    private Field sortField(Field field) {
        return field.transform(builder -> {
            builder.alias(null);
            builder.arguments(sortArguments(field.getArguments()));
            builder.directives(sortDirectives(field.getDirectives()));
            SelectionSet selectionSet = field.getSelectionSet();
            if (selectionSet != null) {
                builder.selectionSet(sortSelectionSet(selectionSet));
            }
        });
    }

    private InlineFragment sortInlineFragment(InlineFragment inlineFragment) {
        return inlineFragment.transform(builder -> {
            builder.directives(sortDirectives(inlineFragment.getDirectives()));
            builder.selectionSet(sortSelectionSet(inlineFragment.getSelectionSet()));
        });
    }

    private FragmentSpread sortFragmentSpread(FragmentSpread fragmentSpread) {
        return fragmentSpread.transform(builder -> builder.directives(sortDirectives(fragmentSpread.getDirectives())));
    }

    private List<VariableDefinition> sortVariableDefinitions(List<VariableDefinition> variableDefinitions) {
        List<VariableDefinition> result = new ArrayList<>(variableDefinitions.size());
        for (VariableDefinition variableDefinition : variableDefinitions) {
            result.add(sortVariableDefinition(variableDefinition));
        }
        result.sort((left, right) -> left.getName().compareTo(right.getName()));
        return result;
    }

    private VariableDefinition sortVariableDefinition(VariableDefinition variableDefinition) {
        return variableDefinition.transform(builder -> {
            Value defaultValue = variableDefinition.getDefaultValue();
            if (defaultValue != null) {
                builder.defaultValue(sortValue(defaultValue));
            }
            builder.directives(sortDirectives(variableDefinition.getDirectives()));
        });
    }

    private List<Directive> sortDirectives(List<Directive> directives) {
        List<Directive> result = new ArrayList<>(directives.size());
        for (Directive directive : directives) {
            result.add(sortDirective(directive));
        }
        result.sort((left, right) -> left.getName().compareTo(right.getName()));
        return result;
    }

    private Directive sortDirective(Directive directive) {
        return directive.transform(builder -> builder.arguments(sortArguments(directive.getArguments())));
    }

    private List<Argument> sortArguments(List<Argument> arguments) {
        List<Argument> result = new ArrayList<>(arguments.size());
        for (Argument argument : arguments) {
            result.add(sortArgument(argument));
        }
        result.sort((left, right) -> left.getName().compareTo(right.getName()));
        return result;
    }

    private Argument sortArgument(Argument argument) {
        return argument.transform(builder -> builder.value(sortValue(argument.getValue())));
    }

    private Value sortValue(Value value) {
        if (value instanceof ObjectValue) {
            return sortObjectValue((ObjectValue) value);
        }
        if (value instanceof ArrayValue) {
            return sortArrayValue((ArrayValue) value);
        }
        return value;
    }

    private ArrayValue sortArrayValue(ArrayValue arrayValue) {
        List<Value> values = new ArrayList<>(arrayValue.getValues().size());
        for (Value value : arrayValue.getValues()) {
            values.add(sortValue(value));
        }
        return arrayValue.transform(builder -> builder.values(values));
    }

    private ObjectValue sortObjectValue(ObjectValue objectValue) {
        List<ObjectField> objectFields = new ArrayList<>(objectValue.getObjectFields().size());
        for (ObjectField objectField : objectValue.getObjectFields()) {
            objectFields.add(sortObjectField(objectField));
        }
        objectFields.sort((left, right) -> left.getName().compareTo(right.getName()));
        return objectValue.transform(builder -> builder.objectFields(objectFields));
    }

    private ObjectField sortObjectField(ObjectField objectField) {
        return objectField.transform(builder -> builder.value(sortValue(objectField.getValue())));
    }

    private int compareSelections(Selection left, Selection right) {
        int typeComparison = Integer.compare(selectionSortType(left), selectionSortType(right));
        if (typeComparison != 0) {
            return typeComparison;
        }
        return selectionSortName(left).compareTo(selectionSortName(right));
    }

    private int selectionSortType(Selection selection) {
        if (selection instanceof Field) {
            return 1;
        }
        if (selection instanceof FragmentSpread) {
            return 2;
        }
        return 3;
    }

    private String selectionSortName(Selection selection) {
        if (selection instanceof Field) {
            return ((Field) selection).getName();
        }
        if (selection instanceof FragmentSpread) {
            return ((FragmentSpread) selection).getName();
        }
        TypeName typeCondition = ((InlineFragment) selection).getTypeCondition();
        return typeCondition == null ? "" : typeCondition.getName();
    }

    private Document dropUnusedQueryDefinitions(Document document, final @Nullable String operationName) {
        List<Definition> definitions = document.getDefinitions();
        List<Definition> wantedDefinitions = new ArrayList<>(definitions.size());
        boolean changed = false;
        for (Definition definition : definitions) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                if (isThisOperation(operationDefinition, operationName)) {
                    wantedDefinitions.add(definition);
                    continue;
                }
                changed = true;
                continue;
            }
            if (definition instanceof FragmentDefinition) {
                wantedDefinitions.add(definition);
                continue;
            }
            changed = true;
            // SDL in a query makes no sense - its gone should it be present
        }
        if (!changed) {
            return document;
        }
        return document.transform(builder -> builder.definitions(wantedDefinitions));
    }

    private boolean isThisOperation(OperationDefinition operationDefinition, @Nullable String operationName) {
        String name = operationDefinition.getName();
        if (operationName == null) {
            return name == null;
        }
        return operationName.equals(name);
    }

    private Document transformDoc(Document document, NodeVisitorStub visitor) {
        AstTransformer astTransformer = new AstTransformer();
        Node<?> newDoc = astTransformer.transform(document, visitor);
        return (Document) newDoc;
    }

}
