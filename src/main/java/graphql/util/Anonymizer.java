package graphql.util;

import graphql.AssertException;
import graphql.Directives;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.Scalars;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldArgumentEnvironment;
import graphql.analysis.QueryVisitorFieldArgumentInputValue;
import graphql.analysis.QueryVisitorFieldArgumentValueEnvironment;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.collect.ImmutableKit;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.AstPrinter;
import graphql.language.AstTransformer;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.IntValue;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.NonNullType;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTransformer;
import graphql.schema.TypeResolver;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeUtil;
import graphql.schema.impl.SchemaUtil;
import org.jspecify.annotations.NullMarked;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.parser.ParserEnvironment.newParserEnvironment;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNullAs;
import static graphql.schema.GraphQLTypeUtil.unwrapOneAs;
import static graphql.schema.idl.SchemaGenerator.createdMockedSchema;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;

/**
 * Util class which converts schemas and optionally queries
 * into anonymized schemas and queries.
 */
@PublicApi
@NullMarked
public class Anonymizer {

    public static class AnonymizeResult {
        private GraphQLSchema schema;
        private List<String> queries;

        public AnonymizeResult(GraphQLSchema schema, List<String> queries) {
            this.schema = schema;
            this.queries = queries;
        }

        public GraphQLSchema getSchema() {
            return schema;
        }

        public List<String> getQueries() {
            return queries;
        }
    }

    public static GraphQLSchema anonymizeSchema(String sdl) {
        return anonymizeSchemaAndQueries(createdMockedSchema(sdl), ImmutableKit.emptyList(), ImmutableKit.emptyMap()).schema;
    }

    public static GraphQLSchema anonymizeSchema(GraphQLSchema schema) {
        return anonymizeSchemaAndQueries(schema, ImmutableKit.emptyList(), ImmutableKit.emptyMap()).schema;
    }

    public static AnonymizeResult anonymizeSchemaAndQueries(String sdl, List<String> queries) {
        return anonymizeSchemaAndQueries(createdMockedSchema(sdl), queries, ImmutableKit.emptyMap());
    }

    public static AnonymizeResult anonymizeSchemaAndQueries(GraphQLSchema schema, List<String> queries) {
        return anonymizeSchemaAndQueries(schema, queries, ImmutableKit.emptyMap());
    }

    public static AnonymizeResult anonymizeSchemaAndQueries(String sdl, List<String> queries, Map<String, Object> variables) {
        return anonymizeSchemaAndQueries(createdMockedSchema(sdl), queries, variables);
    }

    public static AnonymizeResult anonymizeSchemaAndQueries(GraphQLSchema schema, List<String> queries, Map<String, Object> variables) {
        assertNotNull(queries, "queries can't be null");

        AtomicInteger defaultStringValueCounter = new AtomicInteger(1);
        AtomicInteger defaultIntValueCounter = new AtomicInteger(1);

        Map<GraphQLNamedSchemaElement, String> newNameMap = recordNewNamesForSchema(schema);

        // stores a reverse index of anonymized argument name to argument instance
        // this is to handle cases where the fields on implementing types MUST have the same exact argument and default
        // value definitions as the fields on the implemented interface. (argument default values must match exactly)
        Map<String, GraphQLArgument> renamedArgumentsMap = new HashMap<>();

        SchemaTransformer schemaTransformer = new SchemaTransformer();
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference graphQLTypeReference, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLNamedSchemaElement type = (GraphQLNamedSchemaElement) schema.getType(graphQLTypeReference.getName());
                String newName = newNameMap.get(type);
                GraphQLTypeReference newReference = GraphQLTypeReference.typeRef(newName);
                return changeNode(context, newReference);
            }

            @Override
            public TraversalControl visitGraphQLArgument(GraphQLArgument graphQLArgument, TraverserContext<GraphQLSchemaElement> context) {
                String newName = assertNotNull(newNameMap.get(graphQLArgument));

                if (context.getParentNode() instanceof GraphQLFieldDefinition) {
                    // arguments on field definitions must be identical across implementing types and interfaces.
                    if (renamedArgumentsMap.containsKey(newName)) {
                        return changeNode(context, renamedArgumentsMap.get(newName).transform(b -> {
                        }));
                    }
                }

                GraphQLArgument newElement = graphQLArgument.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                    if (graphQLArgument.hasSetDefaultValue()) {
                        Value<?> defaultValueLiteral = ValuesResolver.valueToLiteral(graphQLArgument.getArgumentDefaultValue(), graphQLArgument.getType(), GraphQLContext.getDefault(), Locale.getDefault());
                        builder.defaultValueLiteral(replaceValue(defaultValueLiteral, graphQLArgument.getType(), newNameMap, defaultStringValueCounter, defaultIntValueCounter));
                    }

                    if (graphQLArgument.hasSetValue()) {
                        Value<?> valueLiteral = ValuesResolver.valueToLiteral(graphQLArgument.getArgumentValue(), graphQLArgument.getType(), GraphQLContext.getDefault(), Locale.getDefault());
                        builder.valueLiteral(replaceValue(valueLiteral, graphQLArgument.getType(), newNameMap, defaultStringValueCounter, defaultIntValueCounter));
                    }
                });

                renamedArgumentsMap.put(newName, newElement);
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument graphQLArgument, TraverserContext<GraphQLSchemaElement> context) {
                String newName = assertNotNull(newNameMap.get(graphQLArgument));

                GraphQLAppliedDirectiveArgument newElement = graphQLArgument.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                    if (graphQLArgument.hasSetValue()) {
                        Value<?> valueLiteral = ValuesResolver.valueToLiteral(graphQLArgument.getArgumentValue(), graphQLArgument.getType(), GraphQLContext.getDefault(), Locale.getDefault());
                        builder.valueLiteral(replaceValue(valueLiteral, graphQLArgument.getType(), newNameMap, defaultStringValueCounter, defaultIntValueCounter));
                    }
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType graphQLInterfaceType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLInterfaceType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLInterfaceType));
                GraphQLInterfaceType newElement = graphQLInterfaceType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                GraphQLCodeRegistry.Builder codeRegistry = assertNotNull(context.getVarFromParents(GraphQLCodeRegistry.Builder.class));
                TypeResolver typeResolver = codeRegistry.getTypeResolver(graphQLInterfaceType);
                codeRegistry.typeResolver(newName, typeResolver);
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLEnumType(GraphQLEnumType graphQLEnumType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLEnumType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLEnumType));
                GraphQLEnumType newElement = graphQLEnumType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition enumValueDefinition, TraverserContext<GraphQLSchemaElement> context) {
                String newName = assertNotNull(newNameMap.get(enumValueDefinition));
                GraphQLEnumValueDefinition newElement = enumValueDefinition.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition graphQLFieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                String newName = assertNotNull(newNameMap.get(graphQLFieldDefinition));
                GraphQLFieldDefinition newElement = graphQLFieldDefinition.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLDirective(GraphQLDirective graphQLDirective, TraverserContext<GraphQLSchemaElement> context) {
                if (Directives.isBuiltInDirective(graphQLDirective.getName())) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLDirective));
                GraphQLDirective newElement = graphQLDirective.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective graphQLDirective, TraverserContext<GraphQLSchemaElement> context) {
                if (Directives.DEPRECATED_DIRECTIVE_DEFINITION.getName().equals(graphQLDirective.getName())) {
                    GraphQLAppliedDirectiveArgument reason = GraphQLAppliedDirectiveArgument.newArgument().name("reason")
                            .type(Scalars.GraphQLString)
                            .clearValue()
                            .build();
                    GraphQLAppliedDirective newElement = graphQLDirective.transform(builder -> {
                        builder.description(null).argument(reason);
                    });
                    changeNode(context, newElement);
                    return TraversalControl.ABORT;
                }
                if (Directives.isBuiltInDirective(graphQLDirective.getName())) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLDirective));
                GraphQLAppliedDirective newElement = graphQLDirective.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField graphQLInputObjectField, TraverserContext<GraphQLSchemaElement> context) {
                String newName = assertNotNull(newNameMap.get(graphQLInputObjectField));

                Value<?> defaultValue = null;
                if (graphQLInputObjectField.hasSetDefaultValue()) {
                    defaultValue = ValuesResolver.valueToLiteral(graphQLInputObjectField.getInputFieldDefaultValue(), graphQLInputObjectField.getType(), GraphQLContext.getDefault(), Locale.getDefault());
                    defaultValue = replaceValue(defaultValue, graphQLInputObjectField.getType(), newNameMap, defaultStringValueCounter, defaultIntValueCounter);
                }

                Value<?> finalDefaultValue = defaultValue;
                GraphQLInputObjectField newElement = graphQLInputObjectField.transform(builder -> {
                    builder.name(newName);
                    if (finalDefaultValue != null) {
                        builder.defaultValueLiteral(finalDefaultValue);
                    }
                    builder.description(null);
                    builder.definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType graphQLInputObjectType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLInputObjectType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLInputObjectType));
                GraphQLInputObjectType newElement = graphQLInputObjectType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }


            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType graphQLObjectType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLObjectType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLObjectType));
                GraphQLObjectType newElement = graphQLObjectType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLScalarType(GraphQLScalarType graphQLScalarType, TraverserContext<GraphQLSchemaElement> context) {
                if (ScalarInfo.isGraphqlSpecifiedScalar(graphQLScalarType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLScalarType));
                GraphQLScalarType newElement = graphQLScalarType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                return changeNode(context, newElement);
            }

            @Override
            public TraversalControl visitGraphQLUnionType(GraphQLUnionType graphQLUnionType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLUnionType)) {
                    return TraversalControl.ABORT;
                }
                String newName = assertNotNull(newNameMap.get(graphQLUnionType));
                GraphQLUnionType newElement = graphQLUnionType.transform(builder -> {
                    builder.name(newName).description(null).definition(null);
                });
                GraphQLCodeRegistry.Builder codeRegistry = assertNotNull(context.getVarFromParents(GraphQLCodeRegistry.Builder.class));
                TypeResolver typeResolver = codeRegistry.getTypeResolver(graphQLUnionType);
                codeRegistry.typeResolver(newName, typeResolver);
                return changeNode(context, newElement);
            }
        });

        List<String> newQueries = new ArrayList<>();
        for (String query : queries) {
            String newQuery = rewriteQuery(query, schema, newNameMap, variables);
            newQueries.add(newQuery);
        }
        AnonymizeResult result = new AnonymizeResult(newSchema, newQueries);
        return result;
    }

    private static Value replaceValue(Value valueLiteral, GraphQLInputType argType, Map<GraphQLNamedSchemaElement, String> newNameMap, AtomicInteger defaultStringValueCounter, AtomicInteger defaultIntValueCounter) {
        if (valueLiteral instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) valueLiteral).getValues();
            ArrayValue.Builder newArrayValueBuilder = ArrayValue.newArrayValue();
            for (Value value : values) {
                // [Type!]! -> Type!
                GraphQLInputType unwrappedInputType = unwrapOneAs(unwrapNonNull(argType));
                newArrayValueBuilder.value(replaceValue(value, unwrappedInputType, newNameMap, defaultStringValueCounter, defaultIntValueCounter));
            }
            return newArrayValueBuilder.build();
        } else if (valueLiteral instanceof StringValue) {
            return StringValue.newStringValue("stringValue" + defaultStringValueCounter.getAndIncrement()).build();
        } else if (valueLiteral instanceof IntValue) {
            return IntValue.newIntValue(BigInteger.valueOf(defaultIntValueCounter.getAndIncrement())).build();
        } else if (valueLiteral instanceof EnumValue) {
            GraphQLEnumType enumType = unwrapNonNullAs(argType);
            GraphQLEnumValueDefinition enumValueDefinition = enumType.getValue(((EnumValue) valueLiteral).getName());
            String newName = assertNotNull(newNameMap.get(enumValueDefinition), "No new name found for enum value %s", ((EnumValue) valueLiteral).getName());
            return new EnumValue(newName);
        } else if (valueLiteral instanceof ObjectValue) {
            GraphQLInputObjectType inputObjectType = unwrapNonNullAs(argType);
            ObjectValue.Builder newObjectValueBuilder = ObjectValue.newObjectValue();
            List<ObjectField> objectFields = ((ObjectValue) valueLiteral).getObjectFields();
            for (ObjectField objectField : objectFields) {
                String objectFieldName = objectField.getName();
                Value objectFieldValue = objectField.getValue();
                GraphQLInputObjectField inputObjectTypeField = inputObjectType.getField(objectFieldName);
                GraphQLInputType fieldType = unwrapNonNullAs(inputObjectTypeField.getType());
                ObjectField newObjectField = objectField.transform(builder -> {
                    builder.name(newNameMap.get(inputObjectTypeField));
                    builder.value(replaceValue(objectFieldValue, fieldType, newNameMap, defaultStringValueCounter, defaultIntValueCounter));
                });
                newObjectValueBuilder.objectField(newObjectField);
            }
            return newObjectValueBuilder.build();
        }
        return valueLiteral;
    }

    public static Map<GraphQLNamedSchemaElement, String> recordNewNamesForSchema(GraphQLSchema schema) {
        AtomicInteger objectCounter = new AtomicInteger(1);
        AtomicInteger inputObjectCounter = new AtomicInteger(1);
        AtomicInteger inputObjectFieldCounter = new AtomicInteger(1);
        AtomicInteger fieldCounter = new AtomicInteger(1);
        AtomicInteger scalarCounter = new AtomicInteger(1);
        AtomicInteger directiveCounter = new AtomicInteger(1);
        AtomicInteger argumentCounter = new AtomicInteger(1);
        AtomicInteger interfaceCounter = new AtomicInteger(1);
        AtomicInteger unionCounter = new AtomicInteger(1);
        AtomicInteger enumCounter = new AtomicInteger(1);
        AtomicInteger enumValueCounter = new AtomicInteger(1);

        Map<GraphQLNamedSchemaElement, String> newNameMap = new LinkedHashMap<>();

        Map<String, String> directivesOriginalToNewNameMap = new HashMap<>();
        // DirectiveName.argumentName -> newArgumentName
        Map<String, String> seenArgumentsOnDirectivesMap = new HashMap<>();

        Map<String, List<GraphQLImplementingType>> interfaceToImplementations =
                new SchemaUtil().groupImplementationsForInterfacesAndObjects(schema);

        Consumer<GraphQLNamedSchemaElement> recordDirectiveName = (graphQLDirective) -> {
            String directiveName = graphQLDirective.getName();
            if (directivesOriginalToNewNameMap.containsKey(directiveName)) {
                newNameMap.put(graphQLDirective, directivesOriginalToNewNameMap.get(directiveName));
                return;
            }

            String newName = "Directive" + directiveCounter.getAndIncrement();
            newNameMap.put(graphQLDirective, newName);
            directivesOriginalToNewNameMap.put(directiveName, newName);
        };

        BiConsumer<GraphQLNamedSchemaElement, String> recordDirectiveArgumentName = (graphQLArgument, directiveArgumentKey) -> {
            if (seenArgumentsOnDirectivesMap.containsKey(directiveArgumentKey)) {
                newNameMap.put(graphQLArgument, seenArgumentsOnDirectivesMap.get(directiveArgumentKey));
                return;
            }
            String newName = "argument" + argumentCounter.getAndIncrement();
            newNameMap.put(graphQLArgument, newName);
            seenArgumentsOnDirectivesMap.put(directiveArgumentKey, newName);
        };

        GraphQLTypeVisitor visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLArgument(GraphQLArgument graphQLArgument, TraverserContext<GraphQLSchemaElement> context) {
                String curName = graphQLArgument.getName();
                GraphQLSchemaElement parentNode = context.getParentNode();
                if (parentNode instanceof GraphQLDirective) {
                    // if we already went over the argument for this directive name, no need to add new names
                    String directiveArgumentKey = ((GraphQLDirective) parentNode).getName() + graphQLArgument.getName();
                    recordDirectiveArgumentName.accept(graphQLArgument, directiveArgumentKey);
                    return CONTINUE;
                }

                if (!(parentNode instanceof GraphQLFieldDefinition)) {
                    String newName = "argument" + argumentCounter.getAndIncrement();
                    newNameMap.put(graphQLArgument, newName);
                    return CONTINUE;
                }
                GraphQLFieldDefinition fieldDefinition = (GraphQLFieldDefinition) parentNode;
                String fieldName = fieldDefinition.getName();
                GraphQLImplementingType implementingType = (GraphQLImplementingType) context.getParentContext().getParentNode();
                Set<GraphQLFieldDefinition> matchingInterfaceFieldDefinitions = getSameFields(fieldName, implementingType.getName(), interfaceToImplementations, schema);
                String newName;
                if (matchingInterfaceFieldDefinitions.size() == 0) {
                    newName = "argument" + argumentCounter.getAndIncrement();
                } else {
                    List<GraphQLArgument> matchingArgumentDefinitions = getMatchingArgumentDefinitions(curName, matchingInterfaceFieldDefinitions);
                    if (matchingArgumentDefinitions.size() == 0) {
                        newName = "argument" + argumentCounter.getAndIncrement();
                    } else {
                        if (newNameMap.containsKey(matchingArgumentDefinitions.get(0))) {
                            newName = newNameMap.get(matchingArgumentDefinitions.get(0));
                        } else {
                            newName = "argument" + argumentCounter.getAndIncrement();
                            for (GraphQLArgument argument : matchingArgumentDefinitions) {
                                newNameMap.put(argument, newName);
                            }
                        }
                    }
                }
                newNameMap.put(graphQLArgument, newName);

                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument graphQLArgument, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLSchemaElement parentNode = context.getParentNode();
                if (parentNode instanceof GraphQLAppliedDirective) {
                    // if we already went over the argument for this directive name, no need to add new names
                    String directiveArgumentKey = ((GraphQLAppliedDirective) parentNode).getName() + graphQLArgument.getName();
                    recordDirectiveArgumentName.accept(graphQLArgument, directiveArgumentKey);
                }
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLDirective(GraphQLDirective graphQLDirective, TraverserContext<GraphQLSchemaElement> context) {
                if (Directives.isBuiltInDirective(graphQLDirective)) {
                    return TraversalControl.ABORT;
                }
                recordDirectiveName.accept(graphQLDirective);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective graphQLAppliedDirective, TraverserContext<GraphQLSchemaElement> context) {
                if (Directives.isBuiltInDirective(graphQLAppliedDirective.getName())) {
                    return TraversalControl.ABORT;
                }
                recordDirectiveName.accept(graphQLAppliedDirective);
                return CONTINUE;
            }


            @Override
            public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType graphQLInterfaceType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLInterfaceType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "Interface" + interfaceCounter.getAndIncrement();
                newNameMap.put(graphQLInterfaceType, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLEnumType(GraphQLEnumType graphQLEnumType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLEnumType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "Enum" + enumCounter.getAndIncrement();
                newNameMap.put(graphQLEnumType, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition enumValueDefinition, TraverserContext<GraphQLSchemaElement> context) {
                String newName = "EnumValue" + enumValueCounter.getAndIncrement();
                newNameMap.put(enumValueDefinition, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition graphQLFieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                String fieldName = graphQLFieldDefinition.getName();
                GraphQLImplementingType parentNode = (GraphQLImplementingType) context.getParentNode();
                Set<GraphQLFieldDefinition> sameFields = getSameFields(fieldName, parentNode.getName(), interfaceToImplementations, schema);
                String newName;
                if (sameFields.size() == 0) {
                    newName = "field" + fieldCounter.getAndIncrement();
                } else {
                    if (newNameMap.containsKey(sameFields.iterator().next())) {
                        newName = newNameMap.get(sameFields.iterator().next());
                    } else {
                        newName = "field" + fieldCounter.getAndIncrement();
                        for (GraphQLFieldDefinition fieldDefinition : sameFields) {
                            newNameMap.put(fieldDefinition, newName);
                        }
                    }
                }
                newNameMap.put(graphQLFieldDefinition, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField graphQLInputObjectField, TraverserContext<GraphQLSchemaElement> context) {
                String newName = "inputField" + inputObjectFieldCounter.getAndIncrement();
                newNameMap.put(graphQLInputObjectField, newName);
                return CONTINUE;

            }

            @Override
            public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType graphQLInputObjectType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLInputObjectType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "InputObject" + inputObjectCounter.getAndIncrement();
                newNameMap.put(graphQLInputObjectType, newName);
                return CONTINUE;
            }


            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType graphQLObjectType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLObjectType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "Object" + objectCounter.getAndIncrement();
                newNameMap.put(graphQLObjectType, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLScalarType(GraphQLScalarType graphQLScalarType, TraverserContext<GraphQLSchemaElement> context) {
                if (ScalarInfo.isGraphqlSpecifiedScalar(graphQLScalarType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "Scalar" + scalarCounter.getAndIncrement();
                newNameMap.put(graphQLScalarType, newName);
                return CONTINUE;
            }

            @Override
            public TraversalControl visitGraphQLUnionType(GraphQLUnionType graphQLUnionType, TraverserContext<GraphQLSchemaElement> context) {
                if (Introspection.isIntrospectionTypes(graphQLUnionType)) {
                    return TraversalControl.ABORT;
                }
                String newName = "Union" + unionCounter.getAndIncrement();
                newNameMap.put(graphQLUnionType, newName);
                return CONTINUE;
            }
        };

        SchemaTransformer.transformSchema(schema, visitor);
        return newNameMap;
    }

    private static Set<GraphQLFieldDefinition> getSameFields(String fieldName,
                                                             String objectOrInterfaceName,
                                                             Map<String, List<GraphQLImplementingType>> interfaceToImplementations,
                                                             GraphQLSchema schema
    ) {
        Set<GraphQLFieldDefinition> result = new LinkedHashSet<>();
        Set<String> alreadyChecked = new LinkedHashSet<>();
        getSameFieldsImpl(fieldName, objectOrInterfaceName, interfaceToImplementations, schema, alreadyChecked, result);
        return result;
    }

    private static void getSameFieldsImpl(String fieldName,
                                          String curObjectOrInterface,
                                          Map<String, List<GraphQLImplementingType>> interfaceToImplementations,
                                          GraphQLSchema schema,
                                          Set<String> alreadyChecked,
                                          Set<GraphQLFieldDefinition> result) {
        if (alreadyChecked.contains(curObjectOrInterface)) {
            return;
        }
        alreadyChecked.add(curObjectOrInterface);

        // "up": get all Interfaces
        GraphQLImplementingType type = assertNotNull((GraphQLImplementingType) schema.getType(curObjectOrInterface), "No type found for %s", curObjectOrInterface);
        List<GraphQLNamedOutputType> interfaces = type.getInterfaces();
        getMatchingFieldDefinitions(fieldName, interfaces, result);
        for (GraphQLNamedOutputType interfaze : interfaces) {
            getSameFieldsImpl(fieldName, interfaze.getName(), interfaceToImplementations, schema, alreadyChecked, result);
        }

        // "down": get all Object or Interfaces
        List<GraphQLImplementingType> implementations = interfaceToImplementations.get(curObjectOrInterface);
        if (implementations == null) {
            return;
        }
        getMatchingFieldDefinitions(fieldName, implementations, result);
        for (GraphQLImplementingType implementingType : implementations) {
            getSameFieldsImpl(fieldName, implementingType.getName(), interfaceToImplementations, schema, alreadyChecked, result);
        }
    }

    private static void getMatchingFieldDefinitions(
            String fieldName,
            List<? extends GraphQLType> interfaces,
            Set<GraphQLFieldDefinition> result) {
        for (GraphQLType iface : interfaces) {
            GraphQLImplementingType implementingType = (GraphQLImplementingType) iface;
            if (implementingType.getFieldDefinition(fieldName) != null) {
                result.add(implementingType.getFieldDefinition(fieldName));
            }
        }
    }

    private static List<GraphQLArgument> getMatchingArgumentDefinitions(
            String name,
            Set<GraphQLFieldDefinition> fieldDefinitions) {
        List<GraphQLArgument> result = new ArrayList<>();
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            Optional.ofNullable(fieldDefinition.getArgument(name)).ifPresent(result::add);
        }
        return result;
    }

    private static String rewriteQuery(String query, GraphQLSchema schema, Map<GraphQLNamedSchemaElement, String> newNames, Map<String, Object> variables) {
        AtomicInteger fragmentCounter = new AtomicInteger(1);
        AtomicInteger variableCounter = new AtomicInteger(1);
        Map<Node, String> astNodeToNewName = new LinkedHashMap<>();
        Map<String, String> variableNames = new LinkedHashMap<>();
        Map<Field, GraphQLFieldDefinition> fieldToFieldDefinition = new LinkedHashMap<>();

        Document document = Parser.parse(newParserEnvironment().document(query).build());
        assertUniqueOperation(document);
        QueryTraverser queryTraverser = QueryTraverser.newQueryTraverser().document(document).schema(schema).variables(variables).build();
        queryTraverser.visitDepthFirst(new QueryVisitor() {

            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                if (env.isTypeNameIntrospectionField()) {
                    return;
                }
                fieldToFieldDefinition.put(env.getField(), env.getFieldDefinition());
                String newName = assertNotNull(newNames.get(env.getFieldDefinition()));
                Field field = env.getField();
                astNodeToNewName.put(field, newName);

                List<Directive> directives = field.getDirectives();
                for (Directive directive : directives) {
                    // this is a directive definition
                    GraphQLDirective directiveDefinition = assertNotNull(schema.getDirective(directive.getName()), "%s directive definition not found ", directive.getName());
                    String directiveName = directiveDefinition.getName();
                    String newDirectiveName = assertNotNull(newNames.get(directiveDefinition), "No new name found for directive %s", directiveName);
                    astNodeToNewName.put(directive, newDirectiveName);

                    for (Argument argument : directive.getArguments()) {
                        GraphQLArgument argumentDefinition = directiveDefinition.getArgument(argument.getName());
                        String newArgumentName = assertNotNull(newNames.get(argumentDefinition), "No new name found for directive %s argument %s", directiveName, argument.getName());
                        astNodeToNewName.put(argument, newArgumentName);
                        visitDirectiveArgumentValues(directive, argument.getValue());
                    }
                }
            }

            private void visitDirectiveArgumentValues(Directive directive, Value value) {
                if (value instanceof VariableReference) {
                    String name = ((VariableReference) value).getName();
                    if (!variableNames.containsKey(name)) {
                        String newName = "var" + variableCounter.getAndIncrement();
                        variableNames.put(name, newName);
                    }
                }
            }

            @Override
            public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {
            }

            @Override
            public TraversalControl visitArgumentValue(QueryVisitorFieldArgumentValueEnvironment environment) {
                QueryVisitorFieldArgumentInputValue argumentInputValue = environment.getArgumentInputValue();
                if (argumentInputValue.getValue() instanceof VariableReference) {
                    String name = ((VariableReference) argumentInputValue.getValue()).getName();
                    if (!variableNames.containsKey(name)) {
                        String newName = "var" + variableCounter.getAndIncrement();
                        variableNames.put(name, newName);
                    }
                }
                return CONTINUE;
            }

            @Override
            public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {
                FragmentDefinition fragmentDefinition = queryVisitorFragmentSpreadEnvironment.getFragmentDefinition();
                String newName;
                if (!astNodeToNewName.containsKey(fragmentDefinition)) {
                    newName = "Fragment" + fragmentCounter.getAndIncrement();
                    astNodeToNewName.put(fragmentDefinition, newName);
                } else {
                    newName = astNodeToNewName.get(fragmentDefinition);
                }
                astNodeToNewName.put(queryVisitorFragmentSpreadEnvironment.getFragmentSpread(), newName);
            }

            @Override
            public TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment environment) {
                String newName = assertNotNull(newNames.get(environment.getGraphQLArgument()));
                astNodeToNewName.put(environment.getArgument(), newName);
                return CONTINUE;
            }
        });

        AstTransformer astTransformer = new AstTransformer();
        AtomicInteger aliasCounter = new AtomicInteger(1);
        AtomicInteger defaultStringValueCounter = new AtomicInteger(1);
        AtomicInteger defaultIntValueCounter = new AtomicInteger(1);

        Document newDocument = (Document) astTransformer.transform(document, new NodeVisitorStub() {

            @Override
            public TraversalControl visitDirective(Directive directive, TraverserContext<Node> context) {
                String newName = assertNotNull(astNodeToNewName.get(directive));
                GraphQLDirective directiveDefinition = schema.getDirective(directive.getName());
                context.setVar(GraphQLDirective.class, directiveDefinition);
                return changeNode(context, directive.transform(builder -> builder.name(newName)));
            }

            @Override
            public TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> context) {
                if (node.getName() != null) {
                    return changeNode(context, node.transform(builder -> builder.name("operation")));
                } else {
                    return CONTINUE;
                }
            }

            @Override
            public TraversalControl visitField(Field field, TraverserContext<Node> context) {
                String newAlias = null;
                if (field.getAlias() != null) {
                    newAlias = "alias" + aliasCounter.getAndIncrement();
                }
                String newName;
                if (field.getName().equals(Introspection.TypeNameMetaFieldDef.getName())) {
                    newName = Introspection.TypeNameMetaFieldDef.getName();
                } else {
                    newName = assertNotNull(astNodeToNewName.get(field));
                    context.setVar(GraphQLFieldDefinition.class, assertNotNull(fieldToFieldDefinition.get(field)));
                }
                String finalNewAlias = newAlias;
                return changeNode(context, field.transform(builder -> builder.name(newName).alias(finalNewAlias)));
            }

            @Override
            public TraversalControl visitVariableDefinition(VariableDefinition node, TraverserContext<Node> context) {
                String newName = assertNotNull(variableNames.get(node.getName()));
                VariableDefinition newNode = node.transform(builder -> {
                    builder.name(newName).comments(ImmutableKit.emptyList());

                    // convert variable language type to renamed language type
                    TypeName typeName = TypeUtil.unwrapAll(node.getType());
                    GraphQLNamedType originalType = schema.getTypeAs(typeName.getName());
                    // has the type name changed? (standard scalars such as String don't change)
                    if (newNames.containsKey(originalType)) {
                        String newTypeName = newNames.get(originalType);
                        builder.type(replaceTypeName(node.getType(), newTypeName));
                    }

                    if (node.getDefaultValue() != null) {
                        Value<?> defaultValueLiteral = node.getDefaultValue();
                        GraphQLType graphQLType = fromTypeToGraphQLType(node.getType(), schema);
                        builder.defaultValue(replaceValue(defaultValueLiteral, (GraphQLInputType) graphQLType, newNames, defaultStringValueCounter, defaultIntValueCounter));
                    }
                });

                return changeNode(context, newNode);
            }

            @Override
            public TraversalControl visitVariableReference(VariableReference node, TraverserContext<Node> context) {
                String newName = assertNotNull(variableNames.get(node.getName()), "No new variable name found for %s", node.getName());
                return changeNode(context, node.transform(builder -> builder.name(newName)));
            }

            @Override
            public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
                String newName = assertNotNull(astNodeToNewName.get(node));
                GraphQLType currentCondition = assertNotNull(schema.getType(node.getTypeCondition().getName()));
                String newCondition = newNames.get(currentCondition);
                return changeNode(context, node.transform(builder -> builder.name(newName).typeCondition(new TypeName(newCondition))));
            }

            @Override
            public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
                GraphQLType currentCondition = assertNotNull(schema.getType(node.getTypeCondition().getName()));
                String newCondition = newNames.get(currentCondition);
                return changeNode(context, node.transform(builder -> builder.typeCondition(new TypeName(newCondition))));
            }

            @Override
            public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                String newName = assertNotNull(astNodeToNewName.get(node));
                return changeNode(context, node.transform(builder -> builder.name(newName)));
            }

            @Override
            public TraversalControl visitArgument(Argument argument, TraverserContext<Node> context) {
                GraphQLArgument graphQLArgumentDefinition;
                // An argument is either from a applied query directive or from a field
                if (context.getVarFromParents(GraphQLDirective.class) != null) {
                    GraphQLDirective directiveDefinition = context.getVarFromParents(GraphQLDirective.class);
                    graphQLArgumentDefinition = directiveDefinition.getArgument(argument.getName());
                } else {
                    GraphQLFieldDefinition graphQLFieldDefinition = assertNotNull(context.getVarFromParents(GraphQLFieldDefinition.class));
                    graphQLArgumentDefinition = graphQLFieldDefinition.getArgument(argument.getName());
                }
                GraphQLInputType argumentType = graphQLArgumentDefinition.getType();
                String newName = assertNotNull(astNodeToNewName.get(argument));
                Value newValue = replaceValue(argument.getValue(), argumentType, newNames, defaultStringValueCounter, defaultIntValueCounter);
                return changeNode(context, argument.transform(builder -> builder.name(newName).value(newValue)));
            }
        });
        return AstPrinter.printAstCompact(newDocument);
    }

    // converts language [Type!] to [GraphQLType!] using the exact same GraphQLType instance from
    // the provided schema
    private static GraphQLType fromTypeToGraphQLType(Type type, GraphQLSchema schema) {
        if (type instanceof TypeName) {
            String typeName = ((TypeName) type).getName();
            GraphQLType graphQLType = schema.getType(typeName);
            return assertNotNull(graphQLType, "Schema must contain type %s", typeName);
        } else if (type instanceof NonNullType) {
            return nonNull(fromTypeToGraphQLType(TypeUtil.unwrapOne(type), schema));
        } else if (type instanceof ListType) {
            return GraphQLList.list(fromTypeToGraphQLType(TypeUtil.unwrapOne(type), schema));
        } else {
            return assertShouldNeverHappen();
        }
    }

    // rename a language type. e.g: [[Character!]!] -> [[NewName!]!]
    private static Type replaceTypeName(Type type, String newName) {
        if (type instanceof TypeName) {
            return TypeName.newTypeName(newName).build();
        } else if (type instanceof ListType) {
            return ListType.newListType(replaceTypeName(((ListType) type).getType(), newName)).build();
        } else if (type instanceof NonNullType) {
            return NonNullType.newNonNullType(replaceTypeName(((NonNullType) type).getType(), newName)).build();
        } else {
            return assertShouldNeverHappen();
        }
    }

    private static void assertUniqueOperation(Document document) {
        String operationName = null;
        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                if (operationName != null) {
                    throw new AssertException("Query must have exactly one operation");
                }
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationName = operationDefinition.getName();
            }
        }

    }

}