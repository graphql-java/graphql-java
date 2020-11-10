package graphql.schema.idl;

import graphql.AssertException;
import graphql.Directives;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.InterfaceTypeExtensionDefinition;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ObjectValue;
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
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.TypeResolverProxy;
import graphql.schema.idl.errors.NotAnInputTypeError;
import graphql.schema.idl.errors.NotAnOutputTypeError;
import graphql.util.FpKit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.map;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.NonNullType.newNonNullType;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@Internal
public class SchemaGeneratorHelper {

    /**
     * We pass this around so we know what we have defined in a stack like manner plus
     * it gives us helper functions
     */
    static class BuildContext {
        private final TypeDefinitionRegistry typeRegistry;
        private final RuntimeWiring wiring;
        private final Deque<String> typeStack = new ArrayDeque<>();

        private final Map<String, GraphQLOutputType> outputGTypes = new LinkedHashMap<>();
        private final Map<String, GraphQLInputType> inputGTypes = new LinkedHashMap<>();
        private final Map<String, Object> directiveBehaviourContext = new LinkedHashMap<>();
        private final Set<GraphQLDirective> directives = new LinkedHashSet<>();
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

        TypeDefinition getTypeDefinition(Type type) {
            Optional<TypeDefinition> optionalTypeDefinition = typeRegistry.getType(type);

            return optionalTypeDefinition.orElseThrow(
                    () -> new AssertException(format(" type definition for type '%s' not found", type))
            );
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
            // certain types can be both input and output types, for example enums and scalars
            if (outputType instanceof GraphQLInputType) {
                inputGTypes.put(outputType.getName(), (GraphQLInputType) outputType);
            }
        }

        void putInputType(GraphQLNamedInputType inputType) {
            inputGTypes.put(inputType.getName(), inputType);
            // certain types can be both input and output types, for example enums and scalars
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

        public void setDirectiveDefinition(GraphQLDirective directive) {
            this.directives.add(directive);
        }

        public void setDirectives(Set<GraphQLDirective> directives) {
            this.directives.addAll(directives);
        }

        public Set<GraphQLDirective> getDirectives() {
            return directives;
        }
    }

    static final String NO_LONGER_SUPPORTED = "No longer supported";
    static final DirectiveDefinition DEPRECATED_DIRECTIVE_DEFINITION;
    static final DirectiveDefinition SPECIFIED_BY_DIRECTIVE_DEFINITION;

    private final SchemaGeneratorDirectiveHelper generatorDirectiveHelper = new SchemaGeneratorDirectiveHelper();

    static {
        DEPRECATED_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.DeprecatedDirective.getName())
                .directiveLocation(newDirectiveLocation().name(FIELD_DEFINITION.name()).build())
                .directiveLocation(newDirectiveLocation().name(ENUM_VALUE.name()).build())
                .description(createDescription("Marks the field or enum value as deprecated"))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("reason")
                                .description(createDescription("The reason for the deprecation"))
                                .type(newTypeName().name("String").build())
                                .defaultValue(StringValue.newStringValue().value(NO_LONGER_SUPPORTED).build())
                                .build())
                .build();

        SPECIFIED_BY_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(Directives.SpecifiedByDirective.getName())
                .directiveLocation(newDirectiveLocation().name(SCALAR.name()).build())
                .description(createDescription("Exposes a URL that specifies the behaviour of this scalar."))
                .inputValueDefinition(
                        InputValueDefinition.newInputValueDefinition()
                                .name("url")
                                .description(createDescription("The URL that specifies the behaviour of this scalar."))
                                .type(newNonNullType(newTypeName().name("String").build()).build())
                                .build())
                .build();
    }

    private static Description createDescription(String s) {
        return new Description(s, null, false);
    }

    Object buildValue(Value value, GraphQLType requiredType) {
        if (value == null || value instanceof NullValue) {
            return null;
        }

        if (GraphQLTypeUtil.isNonNull(requiredType)) {
            requiredType = unwrapOne(requiredType);
        }

        Object result = null;
        if (requiredType instanceof GraphQLScalarType) {
            result = parseLiteral(value, (GraphQLScalarType) requiredType);
        } else if (requiredType instanceof GraphQLEnumType && value instanceof EnumValue) {
            result = ((EnumValue) value).getName();
        } else if (requiredType instanceof GraphQLEnumType && value instanceof StringValue) {
            result = ((StringValue) value).getValue();
        } else if (isList(requiredType)) {
            if (value instanceof ArrayValue) {
                result = buildArrayValue(requiredType, (ArrayValue) value);
            } else {
                result = buildArrayValue(requiredType, ArrayValue.newArrayValue().value(value).build());
            }
        } else if (value instanceof ObjectValue && requiredType instanceof GraphQLInputObjectType) {
            result = buildObjectValue((ObjectValue) value, (GraphQLInputObjectType) requiredType);
        } else {
            assertShouldNeverHappen(
                    "cannot build value of type %s from object class %s with instance %s", simplePrint(requiredType), value.getClass().getSimpleName(), String.valueOf(value));
        }
        return result;
    }

    private Object parseLiteral(Value value, GraphQLScalarType requiredType) {
        return requiredType.getCoercing().parseLiteral(value);
    }

    Object buildArrayValue(GraphQLType requiredType, ArrayValue arrayValue) {
        GraphQLType wrappedType = unwrapOne(requiredType);
        Object result = map(arrayValue.getValues(), item -> buildValue(item, wrappedType));
        return result;
    }

    Object buildObjectValue(ObjectValue defaultValue, GraphQLInputObjectType objectType) {
        Map<String, Object> map = new LinkedHashMap<>();
        objectType.getFieldDefinitions().forEach(
                f -> {
                    final Value<?> fieldValueFromDefaultObjectValue = getFieldValueFromObjectValue(defaultValue, f.getName());
                    map.put(f.getName(), fieldValueFromDefaultObjectValue != null ? buildValue(fieldValueFromDefaultObjectValue, f.getType()) : f.getDefaultValue());
                }
        );
        return map;
    }

    Value<?> getFieldValueFromObjectValue(final ObjectValue objectValue, final String fieldName) {
        return objectValue.getObjectFields()
                .stream()
                .filter(dvf -> dvf.getName().equals(fieldName))
                .map(ObjectField::getValue)
                .findFirst()
                .orElse(null);
    }

    String buildDescription(Node<?> node, Description description) {
        if (description != null) {
            return description.getContent();
        }
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
        if (lines.size() == 0) {
            return null;
        }
        return lines.stream().collect(joining("\n"));
    }

    String buildDeprecationReason(List<Directive> directives) {
        directives = Optional.ofNullable(directives).orElse(emptyList());
        Optional<Directive> directive = directives.stream().filter(d -> "deprecated".equals(d.getName())).findFirst();
        if (directive.isPresent()) {
            Map<String, String> args = directive.get().getArguments().stream().collect(toMap(
                    Argument::getName, arg -> ((StringValue) arg.getValue()).getValue()
            ));
            if (args.isEmpty()) {
                return NO_LONGER_SUPPORTED; // default value from spec
            } else {
                // pre flight checks have ensured its valid
                return args.get("reason");
            }
        }
        return null;
    }

    // builds directives from a type and its extensions
    GraphQLDirective buildDirective(BuildContext buildCtx,
                                    Directive directive,
                                    Set<GraphQLDirective> directiveDefinitions,
                                    DirectiveLocation directiveLocation,
                                    GraphqlTypeComparatorRegistry comparatorRegistry) {
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directive.getName())
                .description(buildDescription(directive, null))
                .comparatorRegistry(comparatorRegistry)
                .validLocations(directiveLocation);

        Optional<GraphQLDirective> directiveDefOpt = FpKit.findOne(directiveDefinitions, dd -> dd.getName().equals(directive.getName()));

        GraphQLDirective graphQLDirective = directiveDefOpt.orElseGet(() -> {
            Function<Type, GraphQLInputType> inputTypeFactory = inputType -> buildInputType(buildCtx, inputType);
            return buildDirectiveFromDefinition(buildCtx.getTypeRegistry().getDirectiveDefinition(directive.getName()).get(), inputTypeFactory);
        });

        List<GraphQLArgument> arguments = map(directive.getArguments(), arg -> buildDirectiveArgument(arg, graphQLDirective));

        arguments = transferMissingArguments(arguments, graphQLDirective);
        arguments.forEach(builder::argument);

        return builder.build();
    }

    private GraphQLArgument buildDirectiveArgument(Argument arg, GraphQLDirective directiveDefinition) {
        GraphQLArgument directiveDefArgument = directiveDefinition.getArgument(arg.getName());
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.name(arg.getName());
        GraphQLInputType inputType;
        Object defaultValue;
        inputType = directiveDefArgument.getType();
        defaultValue = directiveDefArgument.getDefaultValue();
        builder.type(inputType);
        builder.defaultValue(defaultValue);

        Object value = buildValue(arg.getValue(), inputType);
        //
        // we put the default value in if the specified is null
        builder.value(value == null ? defaultValue : value);

        return builder.build();
    }

    private List<GraphQLArgument> transferMissingArguments(List<GraphQLArgument> arguments, GraphQLDirective directiveDefinition) {
        Map<String, GraphQLArgument> declaredArgs = FpKit.getByName(arguments, GraphQLArgument::getName, FpKit.mergeFirst());
        List<GraphQLArgument> argumentsOut = new ArrayList<>(arguments);

        for (GraphQLArgument directiveDefArg : directiveDefinition.getArguments()) {
            if (!declaredArgs.containsKey(directiveDefArg.getName())) {
                GraphQLArgument missingArg = GraphQLArgument.newArgument()
                        .name(directiveDefArg.getName())
                        .description(directiveDefArg.getDescription())
                        .definition(directiveDefArg.getDefinition())
                        .type(directiveDefArg.getType())
                        .defaultValue(directiveDefArg.getDefaultValue())
                        .value(directiveDefArg.getDefaultValue())
                        .build();
                argumentsOut.add(missingArg);
            }
        }
        return argumentsOut;
    }

    GraphQLDirective buildDirectiveFromDefinition(DirectiveDefinition directiveDefinition, Function<Type, GraphQLInputType> inputTypeFactory) {

        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(directiveDefinition.getName())
                .definition(directiveDefinition)
                .description(buildDescription(directiveDefinition, directiveDefinition.getDescription()));


        List<DirectiveLocation> locations = buildLocations(directiveDefinition);
        locations.forEach(builder::validLocations);

        List<GraphQLArgument> arguments = map(directiveDefinition.getInputValueDefinitions(),
                arg -> buildDirectiveArgumentFromDefinition(arg, inputTypeFactory));
        arguments.forEach(builder::argument);
        return builder.build();
    }

    private List<DirectiveLocation> buildLocations(DirectiveDefinition directiveDefinition) {
        return map(directiveDefinition.getDirectiveLocations(),
                dl -> DirectiveLocation.valueOf(dl.getName().toUpperCase()));
    }

    private GraphQLArgument buildDirectiveArgumentFromDefinition(InputValueDefinition arg, Function<Type, GraphQLInputType> inputTypeFactory) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(arg.getName())
                .definition(arg);

        GraphQLInputType inputType = inputTypeFactory.apply(arg.getType());
        builder.type(inputType);
        builder.value(buildValue(arg.getDefaultValue(), inputType));
        builder.defaultValue(buildValue(arg.getDefaultValue(), inputType));
        builder.description(buildDescription(arg, arg.getDescription()));
        return builder.build();
    }

    GraphQLInputType buildInputType(BuildContext buildCtx, Type rawType) {

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

    GraphQLInputObjectType buildInputObjectType(BuildContext buildCtx, InputObjectTypeDefinition typeDefinition) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<InputObjectTypeExtensionDefinition> extensions = inputObjectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);

        builder.withDirectives(
                buildDirectives(buildCtx,
                        typeDefinition.getDirectives(),
                        directivesOf(extensions),
                        INPUT_OBJECT,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
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
        inputObjectType = generatorDirectiveHelper.onInputObjectType(inputObjectType, buildCtx.mkBehaviourParams());
        return inputObjectType;
    }

    private GraphQLInputObjectField buildInputField(BuildContext buildCtx, InputValueDefinition fieldDef) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
        fieldBuilder.definition(fieldDef);
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description(buildDescription(fieldDef, fieldDef.getDescription()));
        fieldBuilder.comparatorRegistry(buildCtx.getComparatorRegistry());

        // currently the spec doesnt allow deprecations on InputValueDefinitions but it should!
        //fieldBuilder.deprecate(buildDeprecationReason(fieldDef.getDirectives()));
        GraphQLInputType inputType = buildInputType(buildCtx, fieldDef.getType());
        fieldBuilder.type(inputType);
        Value defaultValue = fieldDef.getDefaultValue();
        if (defaultValue != null) {
            fieldBuilder.defaultValue(buildValue(defaultValue, inputType));
        }

        fieldBuilder.withDirectives(
                buildDirectives(buildCtx,
                        fieldDef.getDirectives(),
                        emptyList(),
                        INPUT_FIELD_DEFINITION,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
        );

        return fieldBuilder.build();
    }

    GraphQLEnumType buildEnumType(BuildContext buildCtx, EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition, typeDefinition.getDescription()));
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
                buildDirectives(buildCtx,
                        typeDefinition.getDirectives(),
                        directivesOf(extensions),
                        ENUM,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
        );

        GraphQLEnumType enumType = builder.build();
        enumType = generatorDirectiveHelper.onEnum(enumType, buildCtx.mkBehaviourParams());
        return enumType;
    }

    private GraphQLEnumValueDefinition buildEnumValue(BuildContext buildCtx,
                                                      EnumTypeDefinition typeDefinition,
                                                      EnumValuesProvider enumValuesProvider,
                                                      EnumValueDefinition evd) {
        String description = buildDescription(evd, evd.getDescription());
        String deprecation = buildDeprecationReason(evd.getDirectives());

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
                        buildDirectives(buildCtx,
                                evd.getDirectives(),
                                emptyList(),
                                ENUM_VALUE,
                                buildCtx.getDirectives(),
                                buildCtx.getComparatorRegistry())
                )
                .build();
    }

    GraphQLScalarType buildScalar(BuildContext buildCtx, ScalarTypeDefinition typeDefinition) {
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
                            buildCtx,
                            typeDefinition.getDirectives(),
                            directivesOf(extensions),
                            SCALAR,
                            buildCtx.getDirectives(),
                            buildCtx.getComparatorRegistry())
                    ));
            scalar = generatorDirectiveHelper.onScalar(scalar, buildCtx.mkBehaviourParams());
        }
        return scalar;
    }

    String getSpecifiedByUrl(ScalarTypeDefinition scalarTypeDefinition, List<ScalarTypeExtensionDefinition> extensions) {
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

    GraphQLDirective[] buildDirectives(BuildContext buildCtx,
                                       List<Directive> directives,
                                       List<Directive> extensionDirectives,
                                       DirectiveLocation directiveLocation,
                                       Set<GraphQLDirective> directiveDefinitions,
                                       GraphqlTypeComparatorRegistry comparatorRegistry) {
        directives = Optional.ofNullable(directives).orElse(emptyList());
        extensionDirectives = Optional.ofNullable(extensionDirectives).orElse(emptyList());
        Set<String> names = new LinkedHashSet<>();

        List<GraphQLDirective> output = new ArrayList<>();
        for (Directive directive : directives) {
            if (!names.contains(directive.getName())) {
                names.add(directive.getName());
                output.add(buildDirective(buildCtx, directive, directiveDefinitions, directiveLocation, comparatorRegistry));
            }
        }
        for (Directive directive : extensionDirectives) {
            if (!names.contains(directive.getName())) {
                names.add(directive.getName());
                output.add(buildDirective(buildCtx, directive, directiveDefinitions, directiveLocation, comparatorRegistry));
            }
        }
        return output.toArray(new GraphQLDirective[0]);
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

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getTypeName();

        return buildOutputType(buildCtx, type);
    }

    GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<InterfaceTypeExtensionDefinition> extensions = interfaceTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);
        builder.withDirectives(
                buildDirectives(buildCtx,
                        typeDefinition.getDirectives(),
                        directivesOf(extensions),
                        OBJECT,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
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
        if (!buildCtx.getCodeRegistry().hasTypeResolver(interfaceType.getName())) {
            TypeResolver typeResolver = getTypeResolverForInterface(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(interfaceType, typeResolver);
        }

        interfaceType = generatorDirectiveHelper.onInterface(interfaceType, buildCtx.mkBehaviourParams());
        return interfaceType;
    }

    GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition, typeDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        List<ObjectTypeExtensionDefinition> extensions = objectTypeExtensions(typeDefinition, buildCtx);
        builder.extensionDefinitions(extensions);
        builder.withDirectives(
                buildDirectives(buildCtx,
                        typeDefinition.getDirectives(),
                        directivesOf(extensions),
                        OBJECT,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
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
        objectType = generatorDirectiveHelper.onObject(objectType, buildCtx.mkBehaviourParams());
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

    GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.definition(typeDefinition);
        builder.name(typeDefinition.getName());
        builder.description(buildDescription(typeDefinition, typeDefinition.getDescription()));
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
                buildDirectives(buildCtx,
                        typeDefinition.getDirectives(),
                        directivesOf(extensions),
                        UNION,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
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
        if (!buildCtx.getCodeRegistry().hasTypeResolver(unionType.getName())) {
            TypeResolver typeResolver = getTypeResolverForUnion(buildCtx, typeDefinition);
            buildCtx.getCodeRegistry().typeResolver(unionType, typeResolver);
        }
        unionType = generatorDirectiveHelper.onUnion(unionType, buildCtx.mkBehaviourParams());
        return unionType;
    }

    /**
     * This is the main recursive spot that builds out the various forms of Output types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @param rawType  the type to be built
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

    GraphQLFieldDefinition buildField(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.definition(fieldDef);
        builder.name(fieldDef.getName());
        builder.description(buildDescription(fieldDef, fieldDef.getDescription()));
        builder.deprecate(buildDeprecationReason(fieldDef.getDirectives()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLDirective[] directives = buildDirectives(buildCtx,
                fieldDef.getDirectives(),
                emptyList(), FIELD_DEFINITION,
                buildCtx.getDirectives(),
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
        if (!buildCtx.getCodeRegistry().hasDataFetcher(coordinates)) {
            DataFetcherFactory dataFetcherFactory = buildDataFetcherFactory(buildCtx, parentType, fieldDef, fieldType, Arrays.asList(directives));
            buildCtx.getCodeRegistry().dataFetcher(coordinates, dataFetcherFactory);
        }
        return fieldDefinition;
    }

    private DataFetcherFactory buildDataFetcherFactory(BuildContext buildCtx,
                                                       TypeDefinition parentType,
                                                       FieldDefinition fieldDef,
                                                       GraphQLOutputType fieldType,
                                                       List<GraphQLDirective> directives) {
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

    GraphQLArgument buildArgument(BuildContext buildCtx, InputValueDefinition valueDefinition) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.definition(valueDefinition);
        builder.name(valueDefinition.getName());
        builder.description(buildDescription(valueDefinition, valueDefinition.getDescription()));
        builder.comparatorRegistry(buildCtx.getComparatorRegistry());

        GraphQLInputType inputType = buildInputType(buildCtx, valueDefinition.getType());
        builder.type(inputType);
        Value defaultValue = valueDefinition.getDefaultValue();
        if (defaultValue != null) {
            builder.defaultValue(buildValue(defaultValue, inputType));
        }

        builder.withDirectives(
                buildDirectives(buildCtx,
                        valueDefinition.getDirectives(),
                        emptyList(),
                        ARGUMENT_DEFINITION,
                        buildCtx.getDirectives(),
                        buildCtx.getComparatorRegistry())
        );

        return builder.build();
    }

    void buildOperations(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
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
        } else {
            query = buildOperation(buildCtx, queryOperation.get());
        }
        schemaBuilder.query(query);

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

    void buildSchemaDirectivesAndExtensions(BuildContext buildCtx, GraphQLSchema.Builder schemaBuilder) {
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();
        List<Directive> schemaDirectiveList = SchemaExtensionsChecker.gatherSchemaDirectives(typeRegistry);
        schemaBuilder.withSchemaDirectives(
                buildDirectives(buildCtx, schemaDirectiveList, emptyList(), DirectiveLocation.SCHEMA, buildCtx.getDirectives(), buildCtx.getComparatorRegistry())
        );

        schemaBuilder.definition(typeRegistry.schemaDefinition().orElse(null));
        schemaBuilder.extensionDefinitions(typeRegistry.getSchemaExtensionDefinitions());
    }

    List<InputObjectTypeExtensionDefinition> inputObjectTypeExtensions(InputObjectTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().inputObjectTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<EnumTypeExtensionDefinition> enumTypeExtensions(EnumTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().enumTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<ScalarTypeExtensionDefinition> scalarTypeExtensions(ScalarTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().scalarTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<InterfaceTypeExtensionDefinition> interfaceTypeExtensions(InterfaceTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().interfaceTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<ObjectTypeExtensionDefinition> objectTypeExtensions(ObjectTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().objectTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    List<UnionTypeExtensionDefinition> unionTypeExtensions(UnionTypeDefinition typeDefinition, BuildContext buildCtx) {
        return buildCtx.getTypeRegistry().unionTypeExtensions().getOrDefault(typeDefinition.getName(), emptyList());
    }

    /**
     * We build the query / mutation / subscription path as a tree of referenced types
     * but then we build the rest of the types specified and put them in as additional types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @return the additional types not referenced from the top level operations
     */
    Set<GraphQLType> buildAdditionalTypes(BuildContext buildCtx) {
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

    Set<GraphQLDirective> buildAdditionalDirectives(BuildContext buildCtx) {
        Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>();
        TypeDefinitionRegistry typeRegistry = buildCtx.getTypeRegistry();

        for (DirectiveDefinition directiveDefinition : typeRegistry.getDirectiveDefinitions().values()) {
            Function<Type, GraphQLInputType> inputTypeFactory = inputType -> buildInputType(buildCtx, inputType);
            GraphQLDirective directive = buildDirectiveFromDefinition(directiveDefinition, inputTypeFactory);
            buildCtx.setDirectiveDefinition(directive);
            additionalDirectives.add(directive);
        }
        return additionalDirectives;
    }

    void addDirectivesIncludedByDefault(TypeDefinitionRegistry typeRegistry) {
        // we synthesize this into the type registry - no need for them to add it
        typeRegistry.add(DEPRECATED_DIRECTIVE_DEFINITION);
        typeRegistry.add(SPECIFIED_BY_DIRECTIVE_DEFINITION);
    }

    private Optional<OperationTypeDefinition> getOperationNamed(String name, Map<String, OperationTypeDefinition> operationTypeDefs) {
        return Optional.ofNullable(operationTypeDefs.get(name));
    }

    private DataFetcher<?> dataFetcherOfLastResort(FieldWiringEnvironment environment) {
        String fieldName = environment.getFieldDefinition().getName();
        return new PropertyDataFetcher(fieldName);
    }

    private List<Directive> directivesOf(List<? extends TypeDefinition> typeDefinitions) {
        return typeDefinitions.stream()
                .map(TypeDefinition::getDirectives).filter(Objects::nonNull)
                .<Directive>flatMap(List::stream).collect(Collectors.toList());
    }
}
