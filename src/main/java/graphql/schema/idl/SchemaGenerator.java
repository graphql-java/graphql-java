package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.TypeResolverProxy;
import graphql.schema.idl.errors.NotAnInputTypeError;
import graphql.schema.idl.errors.NotAnOutputTypeError;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

/**
 * This can generate a working runtime schema from a type registry and runtime wiring
 */
public class SchemaGenerator {

    /**
     * We pass this around so we know what we have defined in a stack like manner plus
     * it gives us helper functions
     */
    class BuildContext {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring wiring;
        private final Stack<String> definitionStack = new Stack<>();

        private final Map<String, GraphQLOutputType> outputGTypes = new HashMap<>();
        private final Map<String, GraphQLInputType> inputGTypes = new HashMap<>();

        BuildContext(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
            this.typeRegistry = typeRegistry;
            this.wiring = wiring;
        }

        public TypeDefinitionRegistry getTypeRegistry() {
            return typeRegistry;
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        TypeDefinition getTypeDefinition(Type type) {
            return typeRegistry.getType(type).get();
        }

        boolean stackContains(TypeInfo typeInfo) {
            return definitionStack.contains(typeInfo.getName());
        }

        void push(TypeInfo typeInfo) {
            definitionStack.push(typeInfo.getName());
        }

        String pop() {
            return definitionStack.pop();
        }

        GraphQLOutputType hasOutputType(TypeDefinition typeDefinition) {
            return outputGTypes.get(typeDefinition.getName());
        }

        GraphQLInputType hasInputType(TypeDefinition typeDefinition) {
            return inputGTypes.get(typeDefinition.getName());
        }

        void put(GraphQLOutputType outputType) {
            outputGTypes.put(outputType.getName(), outputType);
            // certain types can be both input and output types, for example enums
            if (outputType instanceof GraphQLInputType) {
                inputGTypes.put(outputType.getName(), (GraphQLInputType) outputType);
            }
        }

        void put(GraphQLInputType inputType) {
            inputGTypes.put(inputType.getName(), inputType);
            // certain types can be both input and output types, for example enums
            if (inputType instanceof GraphQLOutputType) {
                outputGTypes.put(inputType.getName(), (GraphQLOutputType) inputType);
            }
        }

        RuntimeWiring getWiring() {
            return wiring;
        }
    }

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();

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
        List<GraphQLError> errors = typeChecker.checkTypeRegistry(typeRegistry, wiring);
        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }
        BuildContext buildCtx = new BuildContext(typeRegistry, wiring);

        return makeExecutableSchemaImpl(buildCtx);
    }

    private GraphQLSchema makeExecutableSchemaImpl(BuildContext buildCtx) {
        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;

        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();

        //
        // Schema can be missing if the type is called 'Query'.  Pre flight checks have checked that!
        //
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        if (!typeRegistry.schemaDefinition().isPresent()) {
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            TypeDefinition queryTypeDef = typeRegistry.getType("Query").get();

            query = buildOutputType(buildCtx, new TypeName(queryTypeDef.getName()));
            schemaBuilder.query(query);

            Optional<TypeDefinition> mutationTypeDef = typeRegistry.getType("Mutation");
            if (mutationTypeDef.isPresent()) {
                mutation = buildOutputType(buildCtx, new TypeName(mutationTypeDef.get().getName()));
                schemaBuilder.mutation(mutation);
            }
            Optional<TypeDefinition> subscriptionTypeDef = typeRegistry.getType("Subscription");
            if (subscriptionTypeDef.isPresent()) {
                subscription = buildOutputType(buildCtx, new TypeName(subscriptionTypeDef.get().getName()));
                schemaBuilder.subscription(subscription);
            }
        } else {
            SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
            List<OperationTypeDefinition> operationTypes = schemaDefinition.getOperationTypeDefinitions();

            // pre-flight checked via checker
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            OperationTypeDefinition queryOp = operationTypes.stream().filter(op -> "query".equals(op.getName())).findFirst().get();
            Optional<OperationTypeDefinition> mutationOp = operationTypes.stream().filter(op -> "mutation".equals(op.getName())).findFirst();
            Optional<OperationTypeDefinition> subscriptionOp = operationTypes.stream().filter(op -> "subscription".equals(op.getName())).findFirst();

            query = buildOperation(buildCtx, queryOp);
            schemaBuilder.query(query);

            if (mutationOp.isPresent()) {
                mutation = buildOperation(buildCtx, mutationOp.get());
                schemaBuilder.mutation(mutation);
            }
            if (subscriptionOp.isPresent()) {
                subscription = buildOperation(buildCtx, subscriptionOp.get());
                schemaBuilder.subscription(subscription);
            }
        }

        Set<GraphQLType> additionalTypes = buildAdditionalTypes(buildCtx);

        return schemaBuilder.build(additionalTypes);
    }

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getType();

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
        Set<GraphQLType> additionalTypes = new HashSet<>();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        typeRegistry.types().values().forEach(typeDefinition -> {
            TypeName typeName = new TypeName(typeDefinition.getName());
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
        return additionalTypes;
    }

    /**
     * This is the main recursive spot that builds out the various forms of Output types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @param rawType  the type to be built
     *
     * @return an output type
     */
    @SuppressWarnings("unchecked")
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
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
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
            throw new NotAnOutputTypeError(typeDefinition);
        }

        buildCtx.put(outputType);
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
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
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
            throw new NotAnInputTypeError(typeDefinition);
        }

        buildCtx.put(inputType);
        buildCtx.pop();
        return typeInfo.decorate(inputType);
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {

        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        List<TypeExtensionDefinition> typeExtensions = getTypeExtensionsOf(typeDefinition, buildCtx);

        buildObjectTypeFields(buildCtx, typeDefinition, builder, typeExtensions);

        buildObjectTypeInterfaces(buildCtx, typeDefinition, builder, typeExtensions);

        return builder.build();
    }

    private void buildObjectTypeFields(BuildContext buildCtx, ObjectTypeDefinition typeDefinition, GraphQLObjectType.Builder builder, List<TypeExtensionDefinition> typeExtensions) {
        Map<String, GraphQLFieldDefinition> fieldDefinitions = new LinkedHashMap<>();

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition newFieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            fieldDefinitions.put(newFieldDefinition.getName(), newFieldDefinition);
        });

        // an object consists of the fields it gets from its definition AND its type extensions
        typeExtensions.forEach(typeExt -> typeExt.getFieldDefinitions().forEach(fieldDef -> {
            GraphQLFieldDefinition newFieldDefinition = buildField(buildCtx, typeDefinition, fieldDef);
            //
            // de-dupe here - pre-flight checks ensure all dupes are of the same type
            if (!fieldDefinitions.containsKey(newFieldDefinition.getName())) {
                fieldDefinitions.put(newFieldDefinition.getName(), newFieldDefinition);
            }
        }));

        fieldDefinitions.values().forEach(builder::field);
    }

    private void buildObjectTypeInterfaces(BuildContext buildCtx, ObjectTypeDefinition typeDefinition, GraphQLObjectType.Builder builder, List<TypeExtensionDefinition> typeExtensions) {
        Map<String, GraphQLInterfaceType> interfaces = new LinkedHashMap<>();
        typeDefinition.getImplements().forEach(type -> {
            GraphQLInterfaceType newInterfaceType = buildOutputType(buildCtx, type);
            interfaces.put(newInterfaceType.getName(), newInterfaceType);
        });

        // an object consists of the interfaces it gets from its definition AND its type extensions
        typeExtensions.forEach(typeExt -> typeExt.getImplements().forEach(type -> {
            GraphQLInterfaceType interfaceType = buildOutputType(buildCtx, type);
            //
            // de-dupe here - pre-flight checks ensure all dupes are of the same type
            if (!interfaces.containsKey(interfaceType.getName())) {
                interfaces.put(interfaceType.getName(), interfaceType);
            }
        }));

        interfaces.values().forEach(builder::withInterface);
    }

    private List<TypeExtensionDefinition> getTypeExtensionsOf(ObjectTypeDefinition objectTypeDefinition, BuildContext buildCtx) {
        List<TypeExtensionDefinition> typeExtensionDefinitions = buildCtx.typeRegistry.typeExtensions().get(objectTypeDefinition.getName());
        return typeExtensionDefinitions == null ? Collections.emptyList() : typeExtensionDefinitions;
    }

    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        builder.typeResolver(getTypeResolverForInterface(buildCtx, typeDefinition));

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, typeDefinition, fieldDef)));
        return builder.build();
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));
        builder.typeResolver(getTypeResolverForUnion(buildCtx, typeDefinition));

        typeDefinition.getMemberTypes().forEach(mt -> {
            GraphQLOutputType outputType = buildOutputType(buildCtx, mt);
            if (outputType instanceof GraphQLTypeReference) {
                builder.possibleType((GraphQLTypeReference) outputType);
            } else {
                builder.possibleType((GraphQLObjectType) outputType);
            }
        });
        return builder.build();
    }

    private GraphQLEnumType buildEnumType(BuildContext buildCtx, EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        EnumValuesProvider enumValuesProvider = buildCtx.getWiring().getEnumValuesProviders().get(typeDefinition.getName());
        typeDefinition.getEnumValueDefinitions().forEach(evd -> {
            String description = buildDescription(evd);
            Object value;
            if (enumValuesProvider != null) {
                value = enumValuesProvider.getValue(evd.getName());
                assertNotNull(value, String.format("EnumValuesProvider for %s returned null for %s", typeDefinition.getName(), evd.getName()));
            } else {
                value = evd.getName();
            }
            builder.value(evd.getName(), value, description);
        });
        return builder.build();
    }

    private GraphQLScalarType buildScalar(BuildContext buildCtx, ScalarTypeDefinition typeDefinition) {
        return buildCtx.getWiring().getScalars().get(typeDefinition.getName());
    }

    private GraphQLFieldDefinition buildField(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.name(fieldDef.getName());
        builder.description(buildDescription(fieldDef));

        builder.dataFetcher(buildDataFetcher(buildCtx, parentType, fieldDef));

        fieldDef.getInputValueDefinitions().forEach(inputValueDefinition ->
                builder.argument(buildArgument(buildCtx, inputValueDefinition)));

        GraphQLOutputType outputType = buildOutputType(buildCtx, fieldDef.getType());
        builder.type(outputType);

        return builder.build();
    }

    private DataFetcher buildDataFetcher(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition fieldDef) {
        String fieldName = fieldDef.getName();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        DataFetcher dataFetcher;
        if (wiringFactory.providesDataFetcher(typeRegistry, fieldDef)) {
            dataFetcher = wiringFactory.getDataFetcher(typeRegistry, fieldDef);
            assertNotNull(dataFetcher, "The WiringFactory indicated it provides a data fetcher but then returned null");
        } else {
            dataFetcher = wiring.getDataFetcherForType(parentType.getName()).get(fieldName);
            if (dataFetcher == null) {
                //
                // in the future we could support FieldDateFetcher but we would need a way to indicate that in the schema spec
                // perhaps by a directive
                dataFetcher = new PropertyDataFetcher(fieldName);
            }
        }
        return dataFetcher;
    }

    private GraphQLInputObjectType buildInputObjectType(BuildContext buildCtx, InputObjectTypeDefinition typeDefinition) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        typeDefinition.getInputValueDefinitions().forEach(fieldDef ->
                builder.field(buildInputField(buildCtx, fieldDef)));
        return builder.build();
    }

    private GraphQLInputObjectField buildInputField(BuildContext buildCtx, InputValueDefinition fieldDef) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description(buildDescription(fieldDef));

        fieldBuilder.type(buildInputType(buildCtx, fieldDef.getType()));
        fieldBuilder.defaultValue(buildValue(fieldDef.getDefaultValue()));

        return fieldBuilder.build();
    }


    private GraphQLArgument buildArgument(BuildContext buildCtx, InputValueDefinition valueDefinition) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.name(valueDefinition.getName());
        builder.description(buildDescription(valueDefinition));

        builder.type(buildInputType(buildCtx, valueDefinition.getType()));
        builder.defaultValue(buildValue(valueDefinition.getDefaultValue()));

        return builder.build();
    }

    private Object buildValue(Value value) {
        Object result = null;
        if (value instanceof IntValue) {
            result = ((IntValue) value).getValue();
        } else if (value instanceof FloatValue) {
            result = ((FloatValue) value).getValue();
        } else if (value instanceof StringValue) {
            result = ((StringValue) value).getValue();
        } else if (value instanceof EnumValue) {
            result = ((EnumValue) value).getName();
        } else if (value instanceof BooleanValue) {
            result = ((BooleanValue) value).isValue();
        } else if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            result = arrayValue.getValues().stream().map(this::buildValue).toArray();
        } else if (value instanceof ObjectValue) {
            result = buildObjectValue((ObjectValue) value);
        } else if (value instanceof NullValue) {
            result = null;
        }
        return result;

    }

    private Object buildObjectValue(ObjectValue defaultValue) {
        HashMap<String, Object> map = new LinkedHashMap<>();
        defaultValue.getObjectFields().forEach(of -> map.put(of.getName(), buildValue(of.getValue())));
        return map;
    }

    private TypeResolver getTypeResolverForUnion(BuildContext buildCtx, UnionTypeDefinition unionType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;
        if (wiringFactory.providesTypeResolver(typeRegistry, unionType)) {
            typeResolver = wiringFactory.getTypeResolver(typeRegistry, unionType);
            assertNotNull(typeResolver, "The WiringFactory indicated it provides a type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(unionType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }

        return typeResolver;
    }

    private TypeResolver getTypeResolverForInterface(BuildContext buildCtx, InterfaceTypeDefinition interfaceType) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        RuntimeWiring wiring = buildCtx.getWiring();
        WiringFactory wiringFactory = wiring.getWiringFactory();

        TypeResolver typeResolver;
        if (wiringFactory.providesTypeResolver(typeRegistry, interfaceType)) {
            typeResolver = wiringFactory.getTypeResolver(typeRegistry, interfaceType);
            assertNotNull(typeResolver, "The WiringFactory indicated it provides a type resolver but then returned null");

        } else {
            typeResolver = wiring.getTypeResolvers().get(interfaceType.getName());
            if (typeResolver == null) {
                // this really should be checked earlier via a pre-flight check
                typeResolver = new TypeResolverProxy();
            }
        }

        return typeResolver;
    }


    private String buildDescription(Node node) {
        List<Comment> comments = node.getComments();
        List<String> lines = new ArrayList<>();
        for (Comment comment : comments) {
            String commentLine = comment.getContent();
            if (commentLine.trim().isEmpty()) {
                lines.clear();
            } else {
                lines.add(commentLine);
            }
        }
        if (lines.size() == 0) return null;
        return lines.stream().collect(Collectors.joining("\n"));
    }
}