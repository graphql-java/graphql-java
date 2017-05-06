package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.language.*;
import graphql.schema.*;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.*;

/**
 * This can generate a working runtime schema from a compiled type registry and runtime wiring
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

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        public SchemaDefinition getSchemaDefinition() {
            return typeRegistry.schemaDefinition().get();
        }
    }

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();

    public SchemaGenerator() {
    }

    /**
     * This will take a {@link TypeDefinitionRegistry} and a {@link RuntimeWiring} and put them together to create a executable schema
     *
     * @param typeRegistry this can be obtained via {@link SchemaCompiler#compile(String)}
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

        SchemaDefinition schemaDefinition = buildCtx.getSchemaDefinition();
        List<OperationTypeDefinition> operationTypes = schemaDefinition.getOperationTypeDefinitions();

        // pre-flight checked via checker
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        OperationTypeDefinition queryOp = operationTypes.stream().filter(op -> "query".equals(op.getName())).findFirst().get();
        Optional<OperationTypeDefinition> mutationOp = operationTypes.stream().filter(op -> "mutation".equals(op.getName())).findFirst();

        GraphQLObjectType query = buildOperation(buildCtx, queryOp);
        GraphQLObjectType mutation;

        GraphQLSchema.Builder schemaBuilder = GraphQLSchema
                .newSchema()
                .query(query);

        if (mutationOp.isPresent()) {
            mutation = buildOperation(buildCtx, mutationOp.get());
            schemaBuilder.mutation(mutation);
        }
        return schemaBuilder.build();
    }

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getType();

        return buildOutputType(buildCtx, type);
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
            outputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            outputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
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
            inputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            inputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        }

        buildCtx.put(inputType);
        buildCtx.pop();
        return typeInfo.decorate(inputType);
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, typeDefinition, fieldDef)));

        typeDefinition.getImplements().forEach(type -> builder.withInterface((GraphQLInterfaceType) buildOutputType(buildCtx, type)));
        return builder.build();
    }

    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        builder.typeResolver(getTypeResolver(buildCtx, typeDefinition.getName()));

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, typeDefinition, fieldDef)));
        return builder.build();
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));
        builder.typeResolver(getTypeResolver(buildCtx, typeDefinition.getName()));

        typeDefinition.getMemberTypes().forEach(mt -> {
            TypeDefinition memberTypeDef = buildCtx.getTypeDefinition(mt);
            GraphQLObjectType objectType = buildObjectType(buildCtx, (ObjectTypeDefinition) memberTypeDef);
            builder.possibleType(objectType);
        });
        return builder.build();
    }

    private GraphQLEnumType buildEnumType(EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition));

        typeDefinition.getEnumValueDefinitions().forEach(evd -> builder.value(evd.getName()));
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
        RuntimeWiring wiring = buildCtx.getWiring();
        String fieldName = fieldDef.getName();
        DataFetcher dataFetcher = wiring.getDataFetcherForType(parentType.getName()).get(fieldName);
        if (dataFetcher == null) {
            //
            // in the future we could support FieldDateFetcher but we would need a way to indicate that in the schema spec
            // perhaps by a directive
            dataFetcher = new PropertyDataFetcher(fieldName);
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
        }
        return result;

    }

    private Object buildObjectValue(ObjectValue defaultValue) {
        HashMap<String, Object> map = new LinkedHashMap<>();
        defaultValue.getObjectFields().forEach(of -> map.put(of.getName(), buildValue(of.getValue())));
        return map;
    }

    private TypeResolver getTypeResolver(BuildContext buildCtx, String name) {
        TypeResolver typeResolver = buildCtx.getWiring().getTypeResolvers().get(name);
        if (typeResolver == null) {
            // this really should be checked earlier via a pre-flight check
            typeResolver = new TypeResolverProxy();
        }
        return typeResolver;
    }


    private String buildDescription(Node node) {
        StringBuilder sb = new StringBuilder();
        List<Comment> comments = node.getComments();
        for (int i = 0; i < comments.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(comments.get(i).getContent().trim());
        }
        return sb.toString();
    }
}
