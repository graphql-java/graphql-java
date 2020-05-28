package graphql.schema.idl;

import graphql.Directives;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.UnionTypeExtensionDefinition;
import graphql.language.Value;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.TypeResolverProxy;
import graphql.schema.idl.errors.NotAnInputTypeError;
import graphql.schema.idl.errors.NotAnOutputTypeError;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.util.FpKit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

/**
 * This can generate a working runtime schema from a type registry and runtime wiring
 */
@PublicApi
public class SchemaGenerator {

    /**
     * These options control how the schema generation works
     */
    public static class Options {

        Options() {
        }

        public static Options defaultOptions() {
            return new Options();
        }
    }


    /**
     * We pass this around so we know what we have defined in a stack like manner plus
     * it gives us helper functions
     */
    class BuildContext {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring wiring;
        private final Deque<String> typeStack = new ArrayDeque<>();

        private final Map<String, GraphQLOutputType> outputGTypes = new LinkedHashMap<>();
        private final Map<String, GraphQLInputType> inputGTypes = new LinkedHashMap<>();
        private final Map<String, Object> directiveBehaviourContext = new LinkedHashMap<>();
        private final Set<GraphQLDirective> directiveDefinitions = new LinkedHashSet<>();
        private final GraphQLCodeRegistry.Builder codeRegistry;
        public final Map<String, OperationTypeDefinition> operationTypeDefs;

        BuildContext(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring, Map<String, OperationTypeDefinition> operationTypeDefinitions) {
            this.typeRegistry = typeRegistry;
            this.wiring = wiring;
            this.codeRegistry = GraphQLCodeRegistry.newCodeRegistry(wiring.getCodeRegistry());
            this.operationTypeDefs = operationTypeDefinitions;
        }

        public TypeDefinitionRegistry getTypeRegistry() {
            return typeRegistry;
        }

        @SuppressWarnings({"OptionalGetWithoutIsPresent", "ConstantConditions"})
        TypeDefinition getTypeDefinition(Type type) {
            Optional<TypeDefinition> optionalTypeDefinition = typeRegistry.getType(type);
            assertTrue(optionalTypeDefinition.isPresent(),
                    () -> format(" type definition for type '%s' not found", type));
            return optionalTypeDefinition.get();
        }

        boolean stackContains(TypeInfo typeInfo) {
            return typeStack.contains(typeInfo.getName());
        }

        void push(TypeInfo typeInfo) {
            typeStack.push(typeInfo.getName());
        }

        void pop() {
            typeStack.pop();
        }

        SchemaGeneratorDirectiveHelper.Parameters mkBehaviourParams() {
            return new SchemaGeneratorDirectiveHelper.Parameters(typeRegistry, wiring, directiveBehaviourContext, codeRegistry);
        }

        GraphQLOutputType hasOutputType(TypeDefinition typeDefinition) {
            return outputGTypes.get(typeDefinition.getName());
        }

        GraphQLInputType hasInputType(TypeDefinition typeDefinition) {
            return inputGTypes.get(typeDefinition.getName());
        }

        void putOutputType(GraphQLNamedOutputType outputType) {
            outputGTypes.put(outputType.getName(), outputType);
            // certain types can be both input and output types, for example enums
            if (outputType instanceof GraphQLInputType) {
                inputGTypes.put(outputType.getName(), (GraphQLInputType) outputType);
            }
        }

        void putInputType(GraphQLNamedInputType inputType) {
            inputGTypes.put(inputType.getName(), inputType);
            // certain types can be both input and output types, for example enums
            if (inputType instanceof GraphQLOutputType) {
                outputGTypes.put(inputType.getName(), (GraphQLOutputType) inputType);
            }
        }

        RuntimeWiring getWiring() {
            return wiring;
        }

        GraphqlTypeComparatorRegistry getComparatorRegistry() {
            return wiring.getComparatorRegistry();
        }

        public GraphQLCodeRegistry.Builder getCodeRegistry() {
            return codeRegistry;
        }

        public void setDirectiveDefinitions(Set<GraphQLDirective> directiveDefinitions) {
            this.directiveDefinitions.addAll(directiveDefinitions);
        }

        public Set<GraphQLDirective> getDirectiveDefinitions() {
            return directiveDefinitions;
        }
    }

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();
    private final SchemaGeneratorHelper schemaGeneratorHelper = new SchemaGeneratorHelper();
    private final SchemaGeneratorDirectiveHelper directiveBehaviour = new SchemaGeneratorDirectiveHelper();

    public SchemaGenerator() {
    }

    /**
     * This will take a {@link TypeDefinitionRegistry} and a {@link RuntimeWiring} and put them together to create a executable schema
     *
     * @param typeRegistry this can be obtained via {@link SchemaParser#parse(String)}
     * @param wiring       this can be built using {@link RuntimeWiring#newRuntimeWiring()}
     *
     * @return an executable schema
     *
     * @throws SchemaProblem if there are problems in assembling a schema such as missing type resolvers or no operations defined
     */
    public GraphQLSchema makeExecutableSchema(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {
        return makeExecutableSchema(Options.defaultOptions(), typeRegistry, wiring);
    }

    /**
     * This will take a {@link TypeDefinitionRegistry} and a {@link RuntimeWiring} and put them together to create a executable schema
     * controlled by the provided options.
     *
     * @param options      the controlling options
     * @param typeRegistry this can be obtained via {@link SchemaParser#parse(String)}
     * @param wiring       this can be built using {@link RuntimeWiring#newRuntimeWiring()}
     *
     * @return an executable schema
     *
     * @throws SchemaProblem if there are problems in assembling a schema such as missing type resolvers or no operations defined
     */
    public GraphQLSchema makeExecutableSchema(Options options, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {

        TypeDefinitionRegistry typeRegistryCopy = new TypeDefinitionRegistry();
        typeRegistryCopy.merge(typeRegistry);

        schemaGeneratorHelper.addDirectivesIncludedByDefault(typeRegistryCopy);

        List<GraphQLError> errors = typeChecker.checkTypeRegistry(typeRegistryCopy, wiring);
        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        Map<String, OperationTypeDefinition> operationTypeDefinitions = SchemaExtensionsChecker.gatherOperationDefs(typeRegistry);
        BuildContext buildCtx = new BuildContext(typeRegistryCopy, wiring, operationTypeDefinitions);

        return makeExecutableSchemaImpl(buildCtx);
    }

    private GraphQLSchema makeExecutableSchemaImpl(BuildContext buildCtx) {

        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();

        Set<GraphQLDirective> additionalDirectives = buildAdditionalDirectives(buildCtx);
        schemaBuilder.additionalDirectives(additionalDirectives);
        buildCtx.setDirectiveDefinitions(additionalDirectives);

        buildSchemaDirectivesAndExtensions(buildCtx, schemaBuilder);

        buildOperations(buildCtx, schemaBuilder);

        Set<GraphQLType> additionalTypes = buildAdditionalTypes(buildCtx);
        schemaBuilder.additionalTypes(additionalTypes);

        buildCtx.getCodeRegistry().fieldVisibility(buildCtx.getWiring().getFieldVisibility());

        GraphQLCodeRegistry codeRegistry = buildCtx.getCodeRegistry().build();
        schemaBuilder.codeRegistry(codeRegistry);

        buildCtx.typeRegistry.schemaDefinition().ifPresent(schemaDefinition -> {
            schemaBuilder.description(schemaGeneratorHelper.buildDescription(schemaDefinition, schemaDefinition.getDescription()));
        });
        GraphQLSchema graphQLSchema = schemaBuilder.build();

        Collection<SchemaGeneratorPostProcessing> schemaTransformers = buildCtx.getWiring().getSchemaGeneratorPostProcessings();
        for (SchemaGeneratorPostProcessing postProcessing : schemaTransformers) {
            graphQLSchema = postProcessing.process(graphQLSchema);
        }
        return graphQLSchema;
    }

    private void buildSchemaDirectivesAndExtensions(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        List<Directive> schemaDirectiveList = SchemaExtensionsChecker.gatherSchemaDirectives(typeRegistry);
        schemaBuilder.withSchemaDirectives(
                buildDirectives(schemaDirectiveList, emptyList(), DirectiveLocation.SCHEMA, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        schemaBuilder.definition(typeRegistry.schemaDefinition().orElse(null));
        schemaBuilder.extensionDefinitions(typeRegistry.getSchemaExtensionDefinitions());
    }

    private void buildOperations(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
        //
        // Schema can be missing if the type is called 'Query'.  Pre flight checks have checked that!
        //
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        Map<String, OperationTypeDefinition> operationTypeDefs = buildCtx.operationTypeDefs;

        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;

        Optional<OperationTypeDefinition> queryOperation = getOperationNamed("query", operationTypeDefs);
        if (!queryOperation.isPresent()) {
            @SuppressWarnings({"OptionalGetWithoutIsPresent", "ConstantConditions"})
            TypeDefinition queryTypeDef = typeRegistry.getType("Query").get();

            query = buildOutputType(buildCtx, TypeName.newTypeName().name(queryTypeDef.getName()).build());
            schemaBuilder.query(query);
        } else {
            query = buildOperation(buildCtx, queryOperation.get());
            schemaBuilder.query(query);
        }

        Optional<OperationTypeDefinition> mutationOperation = getOperationNamed("mutation", operationTypeDefs);
        if (!mutationOperation.isPresent()) {
            Optional<TypeDefinition> mutationTypeDef = typeRegistry.getType("Mutation");
            if (mutationTypeDef.isPresent()) {
                mutation = buildOutputType(buildCtx, TypeName.newTypeName().name(mutationTypeDef.get().getName()).build());
                schemaBuilder.mutation(mutation);
            }
        } else {
            mutation = buildOperation(buildCtx, mutationOperation.get());
            schemaBuilder.mutation(mutation);
        }

        Optional<OperationTypeDefinition> subscriptionOperation = getOperationNamed("subscription", operationTypeDefs);
        if (!subscriptionOperation.isPresent()) {
            Optional<TypeDefinition> subscriptionTypeDef = typeRegistry.getType("Subscription");
            if (subscriptionTypeDef.isPresent()) {
                subscription = buildOutputType(buildCtx, TypeName.newTypeName().name(subscriptionTypeDef.get().getName()).build());
                schemaBuilder.subscription(subscription);
            }
        } else {
            subscription = buildOperation(buildCtx, subscriptionOperation.get());
            schemaBuilder.subscription(subscription);
        }
    }

    private Optional<OperationTypeDefinition> getOperationNamed(String name, Map<String, OperationTypeDefinition> operationTypeDefs) {
        return Optional.ofNullable(operationTypeDefs.get(name));
    }

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getTypeName();

        return buildOutputType(buildCtx, type);
    }

    /**
     * We build the query / mutation / subscription path as a tree of referenced types
     * but then we build the rest of the types specified and put them in as additional types
     *
     * @param buildCtx the context we need to work out what we are doing
     *
     * @return the additional types not referenced from the top level operations
     */
    private Set<GraphQLType> buildAdditionalTypes(BuildContext buildCtx) {
        Set<GraphQLType> additionalTypes = new LinkedHashSet<>();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        typeRegistry.types().values().forEach(typeDefinition -> {
            TypeName typeName = TypeName.newTypeName().name(typeDefinition.getName()).build();
            if (typeDefinition instanceof InputObjectTypeDefinition) {
                if (buildCtx.hasInputType(typeDefinition) == null) {
                    additionalTypes.add(buildInputType(buildCtx, typeName));
                }
            } else {
                if (buildCtx.hasOutputType(typeDefinition) == null) {
                    additionalTypes.add(buildOutputType(buildCtx, typeName));
                }
            }
        });
        typeRegistry.scalars().values().forEach(scalarTypeDefinition -> {
            if (ScalarInfo.isGraphqlSpecifiedScalar(scalarTypeDefinition.getName())) {
                return;
            }
            if (buildCtx.hasInputType(scalarTypeDefinition) == null && buildCtx.hasOutputType(scalarTypeDefinition) == null) {
                additionalTypes.add(buildScalar(buildCtx, scalarTypeDefinition));
            }
        });
        return additionalTypes;
    }

    private Set<GraphQLDirective> buildAdditionalDirectives(BuildContext buildCtx) {
        Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        typeRegistry.getDirectiveDefinitions().values().forEach(directiveDefinition -> {
            Function<Type, GraphQLInputType> inputTypeFactory = inputType -> buildInputType(buildCtx, inputType);
            GraphQLDirective directive = schemaGeneratorHelper.buildDirectiveFromDefinition(directiveDefinition, inputTypeFactory);
            additionalDirectives.add(directive);
        });
        return additionalDirectives;
    }

    /**
     * This is the main recursive spot that builds out the various forms of Output types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @param rawType  the type to be built
     *
     * @return an output type
     */
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    private <T extends GraphQLOutputType> T buildOutputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition typeDefinition = buildCtx.getTypeDefinition(rawType);
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        GraphQLOutputType outputType = buildCtx.hasOutputType(typeDefinition);
        if (outputType != null) {
            return typeInfo.decorate(outputType);
        }

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it up later
            // otherwise we will go into an infinite loop
            return typeInfo.decorate(typeRef(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof ObjectTypeDefinition) {
            outputType = buildObjectType(buildCtx, (ObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            outputType = buildInterfaceType(buildCtx, (InterfaceTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            outputType = buildUnionType(buildCtx, (UnionTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            outputType = buildEnumType(buildCtx, (EnumTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            outputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        } else {
            // typeDefinition is not a valid output type
            throw new NotAnOutputTypeError(rawType, typeDefinition);
        }

        buildCtx.putOutputType((GraphQLNamedOutputType) outputType);
        buildCtx.pop();
        return (T) typeInfo.decorate(outputType);
    }

    private GraphQLInputType buildInputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition typeDefinition = buildCtx.getTypeDefinition(rawType);
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        GraphQLInputType inputType = buildCtx.hasInputType(typeDefinition);
        if (inputType != null) {
            return typeInfo.decorate(inputType);
        }

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(typeRef(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof InputObjectTypeDefinition) {
            inputType = buildInputObjectType(buildCtx, (InputObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            inputType = buildEnumType(buildCtx, (EnumTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            inputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        } else {
            // typeDefinition is not a valid InputType
            throw new NotAnInputTypeError(rawType, typeDefinition);
        }

        buildCtx.putInputType((GraphQLNamedInputType) inputType);
        buildCtx.pop();
        return typeInfo.decorate(inputType);
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<ObjectTypeExtensionDefinition> extensions = objectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);
        builder.withDirectives(
                buildDirectives(typeDefinition.getDirectives(),
                        directivesOf(extensions), OBJECT, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            builder.field(fieldDefinition);
        });

        extensions.forEach(extension -> extension.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            if (!builder.hasField(fieldDefinition.getName())) {
                builder.field(fieldDefinition);
            }
        }));

        buildObjectTypeInterfaces(buildCtx, typeDefinition, builder, extensions);

        GraphQLObjectType objectType = builder.build();
        objectType = directiveBehaviour.onObject(objectType, buildCtx.mkBehaviourParams());
        return objectType;
    }

    private void buildObjectTypeInterfaces(BuildContext buildCtx,
                                           ObjectTypeDefinition typeDefinition,
                                           GraphQLObjectType.Builder builder,
                                           List<ObjectTypeExtensionDefinition> extensions) {
        Map<String, GraphQLOutputType> interfaces = new LinkedHashMap<>();
        typeDefinition.getImplements().forEach(type -> {
            GraphQLNamedOutputType newInterfaceType = buildOutputType(buildCtx, type);
            interfaces.put(newInterfaceType.getName(), newInterfaceType);
        });

        extensions.forEach(extension -> extension.getImplements().forEach(type -> {
            GraphQLInterfaceType interfaceType = buildOutputType(buildCtx, type);
            if (!interfaces.containsKey(interfaceType.getName())) {
                interfaces.put(interfaceType.getName(), interfaceType);
            }
        }));

        interfaces.values().forEach(interfaze -> {
            if (interfaze instanceof GraphQLInterfaceType) {
                builder.withInterface((GraphQLInterfaceType) interfaze);
                return;
            }
            if (interfaze instanceof GraphQLTypeReference) {
                builder.withInterface((GraphQLTypeReference) interfaze);
            }
        });
    }

    private void buildInterfaceTypeInterfaces(BuildContext buildCtx,
                                              InterfaceTypeDefinition typeDefinition,
                                              GraphQLInterfaceType.Builder builder,
                                              List<InterfaceTypeExtensionDefinition> extensions) {
        Map<String, GraphQLOutputType> interfaces = new LinkedHashMap<>();
        typeDefinition.getImplements().forEach(type -> {
            GraphQLNamedOutputType newInterfaceType = buildOutputType(buildCtx, type);
            interfaces.put(newInterfaceType.getName(), newInterfaceType);
        });

        extensions.forEach(extension -> extension.getImplements().forEach(type -> {
            GraphQLInterfaceType interfaceType = buildOutputType(buildCtx, type);
            if (!interfaces.containsKey(interfaceType.getName())) {
                interfaces.put(interfaceType.getName(), interfaceType);
            }
        }));

        interfaces.values().forEach(interfaze -> {
            if (interfaze instanceof GraphQLInterfaceType) {
                builder.withInterface((GraphQLInterfaceType) interfaze);
                return;
            }
            if (interfaze instanceof GraphQLTypeReference) {
                builder.withInterface((GraphQLTypeReference) interfaze);
            }
        });
    }

    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());


        List<InterfaceTypeExtensionDefinition> extensions = interfaceTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);
        builder.withDirectives(
                buildDirectives(typeDefinition.getDirectives(),
                        directivesOf(extensions), OBJECT, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            builder.field(fieldDefinition);
        });

        extensions.forEach(extension -> extension.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition fieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            if (!builder.hasField(fieldDefinition.getName())) {
                builder.field(fieldDefinition);
            }
        }));

        buildInterfaceTypeInterfaces(buildCtx, typeDefinition, builder, extensions);

        GraphQLInterfaceType interfaceType = builder.build();
        if (!buildCtx.codeRegistry.hasTypeResolver(interfaceType.getName())) {
            TypeResolver typeResolver = getTypeResolverForInterface(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(interfaceType, typeResolver);
        }

        interfaceType = directiveBehaviour.onInterface(interfaceType, buildCtx.mkBehaviourParams());
        return interfaceType;
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<UnionTypeExtensionDefinition> extensions = unionTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);

        typeDefinition.getMemberTypes().forEach(mt -> {
            GraphQLOutputType outputType = buildOutputType(buildCtx, mt);
            if (outputType instanceof GraphQLTypeReference) {
                builder.possibleType((GraphQLTypeReference) outputType);
            } else {
                builder.possibleType((GraphQLObjectType) outputType);
            }
        });

        builder.withDirectives(
                buildDirectives(typeDefinition.getDirectives(),
                        directivesOf(extensions), UNION, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        extensions.forEach(extension -> extension.getMemberTypes().forEach(mt -> {
                    GraphQLNamedOutputType outputType = buildOutputType(buildCtx, mt);
                    if (!builder.containType(outputType.getName())) {
                        if (outputType instanceof GraphQLTypeReference) {
                            builder.possibleType((GraphQLTypeReference) outputType);
                        } else {
                            builder.possibleType((GraphQLObjectType) outputType);
                        }
                    }
                }
        ));

        GraphQLUnionType unionType = builder.build();
        if (!buildCtx.codeRegistry.hasTypeResolver(unionType.getName())) {
            TypeResolver typeResolver = getTypeResolverForUnion(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(unionType, typeResolver);
        }
        unionType = directiveBehaviour.onUnion(unionType, buildCtx.mkBehaviourParams());
        return unionType;
    }

    private GraphQLEnumType buildEnumType(BuildContext buildCtx, EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<EnumTypeExtensionDefinition> extensions = enumTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);

        EnumValuesProvider enumValuesProvider = buildCtx.getWiring().getEnumValuesProviders().get(typeDefinition.getName());
        typeDefinition.getEnumValueDefinitions().forEach(evd -> {
            GraphQLEnumValueDefinition enumValueDefinition = buildEnumValue(buildCtx, typeDefinition, enumValuesProvider, evd);
            builder.value(enumValueDefinition);
        });

        extensions.forEach(extension -> extension.getEnumValueDefinitions().forEach(evd -> {
            GraphQLEnumValueDefinition enumValueDefinition = buildEnumValue(buildCtx, typeDefinition, enumValuesProvider, evd);
            if (!builder.hasValue(enumValueDefinition.getName())) {
                builder.value(enumValueDefinition);
            }
        }));

        builder.withDirectives(
                buildDirectives(typeDefinition.getDirectives(),
                        directivesOf(extensions), ENUM, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        GraphQLEnumType enumType = builder.build();
        enumType = directiveBehaviour.onEnum(enumType, buildCtx.mkBehaviourParams());
        return enumType;
    }

    private GraphQLEnumValueDefinition buildEnumValue(BuildContext buildCtx, EnumTypeDefinition
            typeDefinition, EnumValuesProvider enumValuesProvider, EnumValueDefinition evd) {
        String description = schemaGeneratorHelper.buildDescription(evd, evd.getDescription());
        String deprecation = schemaGeneratorHelper.buildDeprecationReason(evd.getDirectives());

        Object value;
        if (enumValuesProvider != null) {
            value = enumValuesProvider.getValue(evd.getName());
            assertNotNull(value,
                    () -> format("EnumValuesProvider for %s returned null for %s", typeDefinition.getName(), evd.getName()));
        } else {
            value = evd.getName();
        }
        return newEnumValueDefinition()
                .name(evd.getName())
                .value(value)
                .description(description)
                .deprecationReason(deprecation)
                .definition(evd)
                .comparatorRegistry(buildCtx.getComparatorRegistry())
                .withDirectives(
                        buildDirectives(evd.getDirectives(),
                                emptyList(), ENUM_VALUE, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
                )
                .build();
    }

    private GraphQLScalarType buildScalar(BuildContext buildCtx, ScalarTypeDefinition typeDefinition) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring runtimeWiring = buildCtx.getWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();
        List<ScalarTypeExtensionDefinition> extensions = scalarTypeExtensions(typeDefinition, buildCtx);

        ScalarWiringEnvironment environment = new ScalarWiringEnvironment(typeRegistry, typeDefinition, extensions);

        GraphQLScalarType scalar;
        if (wiringFactory.providesScalar(environment)) {
            scalar = wiringFactory.getScalar(environment);
        } else {
            scalar = buildCtx.getWiring().getScalars().get(typeDefinition.getName());
        }

        if (!ScalarInfo.isGraphqlSpecifiedScalar(scalar)) {
            scalar = scalar.transform(builder -> builder
                    .definition(typeDefinition)
                    .comparatorRegistry(buildCtx.getComparatorRegistry())
                    .specifiedByUrl(getSpecifiedByUrl(typeDefinition, extensions))
                    .withDirectives(buildDirectives(
                            typeDefinition.getDirectives(),
                            directivesOf(extensions),
                            SCALAR,
                            buildCtx.getDirectiveDefinitions(),
                            buildCtx.getComparatorRegistry())
                    ));
            scalar = directiveBehaviour.onScalar(scalar, buildCtx.mkBehaviourParams());
        }
        return scalar;
    }

    private String getSpecifiedByUrl(ScalarTypeDefinition scalarTypeDefinition, List<ScalarTypeExtensionDefinition> extensions) {
        List<Directive> allDirectives = new ArrayList<>(scalarTypeDefinition.getDirectives());
        extensions.forEach(extension -> allDirectives.addAll(extension.getDirectives()));
        Optional<Directive> specifiedByDirective = FpKit.findOne(allDirectives,
                directiveDefinition -> directiveDefinition.getName().equals(Directives.SpecifiedByDirective.getName()));
        if (!specifiedByDirective.isPresent()) {
            return null;
        }
        Argument urlArgument = specifiedByDirective.get().getArgument("url");
        StringValue url = (StringValue) urlArgument.getValue();
        return url.getValue();
    }

    private GraphQLFieldDefinition buildField(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition
            fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.definition(fieldDef);
        builder.name(fieldDef.getName());
        builder.description(schemaGeneratorHelper.buildDescription(fieldDef, fieldDef.getDescription()));
        builder.deprecate(schemaGeneratorHelper.buildDeprecationReason(fieldDef.getDirectives()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLDirective[] directives = buildDirectives(fieldDef.getDirectives(),
                emptyList(), DirectiveLocation.FIELD_DEFINITION,
                buildCtx.getDirectiveDefinitions(),
                buildCtx.getComparatorRegistry());
        builder.withDirectives(
                directives
        );

        fieldDef.getInputValueDefinitions().forEach(inputValueDefinition ->
                builder.argument(buildArgument(buildCtx, inputValueDefinition)));

        GraphQLOutputType fieldType = buildOutputType(buildCtx, fieldDef.getType());
        builder.type(fieldType);

        GraphQLFieldDefinition fieldDefinition = builder.build();
        // if they have already wired in a fetcher - then leave it alone
        FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType.getName(), fieldDefinition.getName());
        if (!buildCtx.codeRegistry.hasDataFetcher(coordinates)) {
            DataFetcherFactory dataFetcherFactory = buildDataFetcherFactory(buildCtx, parentType, fieldDef, fieldType, Arrays.asList(directives));
            buildCtx.getCodeRegistry().dataFetcher(coordinates, dataFetcherFactory);
        }
        return fieldDefinition;
    }

    private DataFetcherFactory buildDataFetcherFactory(BuildContext buildCtx, TypeDefinition
            parentType, FieldDefinition fieldDef, GraphQLOutputType fieldType, List<GraphQLDirective> directives) {
        String fieldName = fieldDef.getName();
        String parentTypeName = parentType.getName();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring runtimeWiring = buildCtx.getWiring();
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();

        FieldWiringEnvironment wiringEnvironment = new FieldWiringEnvironment(typeRegistry, parentType, fieldDef, fieldType, directives);

        DataFetcherFactory<?> dataFetcherFactory;
        if (wiringFactory.providesDataFetcherFactory(wiringEnvironment)) {
            dataFetcherFactory = wiringFactory.getDataFetcherFactory(wiringEnvironment);
            assertNotNull(dataFetcherFactory, () -> "The WiringFactory indicated it provides a data fetcher factory but then returned null");
        } else {
            //
            // ok they provide a data fetcher directly
            DataFetcher<?> dataFetcher;
            if (wiringFactory.providesDataFetcher(wiringEnvironment)) {
                dataFetcher = wiringFactory.getDataFetcher(wiringEnvironment);
                assertNotNull(dataFetcher, () -> "The WiringFactory indicated it provides a data fetcher but then returned null");
            } else {
                dataFetcher = runtimeWiring.getDataFetcherForType(parentTypeName).get(fieldName);
                if (dataFetcher == null) {
                    dataFetcher = runtimeWiring.getDefaultDataFetcherForType(parentTypeName);
                    if (dataFetcher == null) {
                        dataFetcher = wiringFactory.getDefaultDataFetcher(wiringEnvironment);
                        if (dataFetcher == null) {
                            dataFetcher = dataFetcherOfLastResort(wiringEnvironment);
                        }
                    }
                }
            }
            dataFetcherFactory = DataFetcherFactories.useDataFetcher(dataFetcher);
        }
        return dataFetcherFactory;
    }

    private DataFetcher<?> dataFetcherOfLastResort(FieldWiringEnvironment environment) {
        String fieldName = environment.getFieldDefinition().getName();
        return new PropertyDataFetcher(fieldName);
    }

    private GraphQLInputObjectType buildInputObjectType(BuildContext buildCtx, InputObjectTypeDefinition
            typeDefinition) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<InputObjectTypeExtensionDefinition> extensions = inputObjectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);

        builder.withDirectives(
                buildDirectives(typeDefinition.getDirectives(),
                        directivesOf(extensions), INPUT_OBJECT, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        typeDefinition.getInputValueDefinitions().forEach(inputValue ->
                builder.field(buildInputField(buildCtx, inputValue)));

        extensions.forEach(extension -> extension.getInputValueDefinitions().forEach(inputValueDefinition -> {
            GraphQLInputObjectField inputField = buildInputField(buildCtx, inputValueDefinition);
            if (!builder.hasField(inputField.getName())) {
                builder.field(inputField);
            }
        }));

        GraphQLInputObjectType inputObjectType = builder.build();
        inputObjectType = directiveBehaviour.onInputObjectType(inputObjectType, buildCtx.mkBehaviourParams());
        return inputObjectType;
    }

    private GraphQLInputObjectField buildInputField(BuildContext buildCtx, InputValueDefinition fieldDef) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
        fieldBuilder.definition(fieldDef);
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description(schemaGeneratorHelper.buildDescription(fieldDef, fieldDef.getDescription()));
        fieldBuilder.comparatorRegistry(buildCtx.getComparatorRegistry());

        // currently the spec doesnt allow deprecations on InputValueDefinitions but it should!
        //fieldBuilder.deprecate(buildDeprecationReason(fieldDef.getDirectives()));
        GraphQLInputType inputType = buildInputType(buildCtx, fieldDef.getType());
        fieldBuilder.type(inputType);
        Value defaultValue = fieldDef.getDefaultValue();
        if (defaultValue != null) {
            fieldBuilder.defaultValue(schemaGeneratorHelper.buildValue(defaultValue, inputType));
        }

        fieldBuilder.withDirectives(
                buildDirectives(fieldDef.getDirectives(),
                        emptyList(), INPUT_FIELD_DEFINITION, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        return fieldBuilder.build();
    }

    private GraphQLArgument buildArgument(BuildContext buildCtx, InputValueDefinition valueDefinition) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.definition(valueDefinition);
        builder.name(valueDefinition.getName());
        builder.description(schemaGeneratorHelper.buildDescription(valueDefinition, valueDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLInputType inputType = buildInputType(buildCtx, valueDefinition.getType());
        builder.type(inputType);
        Value defaultValue = valueDefinition.getDefaultValue();
        if (defaultValue != null) {
            builder.defaultValue(schemaGeneratorHelper.buildValue(defaultValue, inputType));
        }

        builder.withDirectives(
                buildDirectives(valueDefinition.getDirectives(),
                        emptyList(), ARGUMENT_DEFINITION, buildCtx.getDirectiveDefinitions(), buildCtx.getComparatorRegistry())
        );

        return builder.build();
    }

    @SuppressWarnings("Duplicates")
    private TypeResolver getTypeResolverForUnion(BuildContext buildCtx, UnionTypeDefinition unionType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;
        UnionWiringEnvironment environment = new UnionWiringEnvironment(typeRegistry, unionType);

        if (wiringFactory.providesTypeResolver(environment)) {
            typeResolver = wiringFactory.getTypeResolver(environment);
            assertNotNull(typeResolver, () -> "The WiringFactory indicated it union provides a type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(unionType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }

        return typeResolver;
    }

    @SuppressWarnings("Duplicates")
    private TypeResolver getTypeResolverForInterface(BuildContext buildCtx, InterfaceTypeDefinition interfaceType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;

        InterfaceWiringEnvironment environment = new InterfaceWiringEnvironment(typeRegistry, interfaceType);

        if (wiringFactory.providesTypeResolver(environment)) {
            typeResolver = wiringFactory.getTypeResolver(environment);
            assertNotNull(typeResolver, () -> "The WiringFactory indicated it provides a interface type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(interfaceType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }
        return typeResolver;
    }


    private GraphQLDirective[] buildDirectives(
            List<Directive> directives,
            List<Directive> extensionDirectives,
            DirectiveLocation directiveLocation,
            Set<GraphQLDirective> directiveDefinitions,
            GraphqlTypeComparatorRegistry comparatorRegistry) {
        directives = directives == null ? emptyList() : directives;
        extensionDirectives = extensionDirectives == null ? emptyList() : extensionDirectives;
        Set<String> names = new LinkedHashSet<>();

        List<GraphQLDirective> output = new ArrayList<>();
        for (Directive directive : directives) {
            if (!names.contains(directive.getName())) {
                names.add(directive.getName());
                output.add(schemaGeneratorHelper.buildDirective(directive, directiveDefinitions, directiveLocation, comparatorRegistry));
            }
        }
        for (Directive directive : extensionDirectives) {
            if (!names.contains(directive.getName())) {
                names.add(directive.getName());
                output.add(schemaGeneratorHelper.buildDirective(directive, directiveDefinitions, directiveLocation, comparatorRegistry));
            }
        }
        return output.toArray(new GraphQLDirective[0]);
    }


    private List<ObjectTypeExtensionDefinition> objectTypeExtensions(ObjectTypeDefinition
                                                                             typeDefinition, BuildContext buildCtx) {
        return nvl(buildCtx.typeRegistry.objectTypeExtensions().get(typeDefinition.getName()));
    }

    private List<InterfaceTypeExtensionDefinition> interfaceTypeExtensions(InterfaceTypeDefinition
                                                                                   typeDefinition, BuildContext buildCtx) {
        return nvl(buildCtx.typeRegistry.interfaceTypeExtensions().get(typeDefinition.getName()));
    }

    private List<UnionTypeExtensionDefinition> unionTypeExtensions(UnionTypeDefinition typeDefinition, BuildContext
            buildCtx) {
        return nvl(buildCtx.typeRegistry.unionTypeExtensions().get(typeDefinition.getName()));
    }

    private List<EnumTypeExtensionDefinition> enumTypeExtensions(EnumTypeDefinition typeDefinition, BuildContext
            buildCtx) {
        return nvl(buildCtx.typeRegistry.enumTypeExtensions().get(typeDefinition.getName()));
    }

    private List<ScalarTypeExtensionDefinition> scalarTypeExtensions(ScalarTypeDefinition
                                                                             typeDefinition, BuildContext buildCtx) {
        return nvl(buildCtx.typeRegistry.scalarTypeExtensions().get(typeDefinition.getName()));
    }

    private List<InputObjectTypeExtensionDefinition> inputObjectTypeExtensions(InputObjectTypeDefinition
                                                                                       typeDefinition, BuildContext buildCtx) {
        return nvl(buildCtx.typeRegistry.inputObjectTypeExtensions().get(typeDefinition.getName()));
    }

    private <T> List<T> nvl(List<T> list) {
        return list == null ? emptyList() : list;
    }

    private List<Directive> directivesOf(List<? extends TypeDefinition> typeDefinition) {
        Stream<Directive> directiveStream = typeDefinition.stream()
                .map(TypeDefinition::getDirectives).filter(Objects::nonNull)
                .flatMap(List::stream);
        return directiveStream.collect(Collectors.toList());
    }

}