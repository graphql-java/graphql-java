package graphql.introspection;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NodeDirectivesBuilder;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.parser.Parser;
import graphql.schema.idl.ScalarInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.idl.DirectiveInfo.isGraphqlSpecifiedDirective;

@SuppressWarnings("unchecked")
@PublicApi
public class IntrospectionResultToSchema {

    /**
     * Returns a IDL Document that represents the schema as defined by the introspection execution result
     *
     * @param introspectionResult the result of an introspection query on a schema
     *
     * @return a IDL Document of the schema
     */
    public Document createSchemaDefinition(ExecutionResult introspectionResult) {
        if (!introspectionResult.isDataPresent()) {
            return null;
        }

        Map<String, Object> introspectionResultMap = introspectionResult.getData();
        return createSchemaDefinition(introspectionResultMap);
    }


    /**
     * Returns a IDL Document that represents the schema as defined by the introspection result map
     *
     * @param introspectionResult the result of an introspection query on a schema
     *
     * @return a IDL Document of the schema
     */
    @SuppressWarnings("unchecked")
    public Document createSchemaDefinition(Map<String, Object> introspectionResult) {
        assertTrue(introspectionResult.get("__schema") != null, () -> "__schema expected");
        Map<String, Object> schema = (Map<String, Object>) introspectionResult.get("__schema");


        Map<String, Object> queryType = (Map<String, Object>) schema.get("queryType");
        assertNotNull(queryType, () -> "queryType expected");
        TypeName query = TypeName.newTypeName().name((String) queryType.get("name")).build();
        boolean nonDefaultQueryName = !"Query".equals(query.getName());

        SchemaDefinition.Builder schemaDefinition = SchemaDefinition.newSchemaDefinition();
        schemaDefinition.description(toDescription(schema));
        schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("query").typeName(query).build());

        Map<String, Object> mutationType = (Map<String, Object>) schema.get("mutationType");
        boolean nonDefaultMutationName = false;
        if (mutationType != null) {
            TypeName mutation = TypeName.newTypeName().name((String) mutationType.get("name")).build();
            nonDefaultMutationName = !"Mutation".equals(mutation.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("mutation").typeName(mutation).build());
        }

        Map<String, Object> subscriptionType = (Map<String, Object>) schema.get("subscriptionType");
        boolean nonDefaultSubscriptionName = false;
        if (subscriptionType != null) {
            TypeName subscription = TypeName.newTypeName().name(((String) subscriptionType.get("name"))).build();
            nonDefaultSubscriptionName = !"Subscription".equals(subscription.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("subscription").typeName(subscription).build());
        }

        Document.Builder document = Document.newDocument();
        if (nonDefaultQueryName || nonDefaultMutationName || nonDefaultSubscriptionName) {
            document.definition(schemaDefinition.build());
        }

        List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
        for (Map<String, Object> type : types) {
            TypeDefinition typeDefinition = createTypeDefinition(type);
            if (typeDefinition == null) {
                continue;
            }
            document.definition(typeDefinition);
        }

        List<Map<String, Object>> directives = (List<Map<String, Object>>) schema.get("directives");
        if (directives != null) {
            for (Map<String, Object> directive : directives) {
                DirectiveDefinition directiveDefinition = createDirective(directive);
                if (directiveDefinition == null) {
                    continue;
                }
                document.definition(directiveDefinition);
            }
        }

        return document.build();
    }

    private DirectiveDefinition createDirective(Map<String, Object> input) {
        String directiveName = (String) input.get("name");
        if (isGraphqlSpecifiedDirective(directiveName)) {
            return null;
        }

        DirectiveDefinition.Builder directiveDefBuilder = DirectiveDefinition.newDirectiveDefinition();
        directiveDefBuilder
                .name(directiveName)
                .description(toDescription(input));

        List<Object> locations = (List<Object>) input.get("locations");
        List<DirectiveLocation> directiveLocations = createDirectiveLocations(locations);
        directiveDefBuilder.directiveLocations(directiveLocations);


        List<Map<String, Object>> args = (List<Map<String, Object>>) input.get("args");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
        directiveDefBuilder.inputValueDefinitions(inputValueDefinitions);
        Optional.ofNullable((Boolean) input.get("isRepeatable")).ifPresent(value -> directiveDefBuilder.repeatable(value));

        return directiveDefBuilder.build();
    }

    private List<DirectiveLocation> createDirectiveLocations(List<Object> locations) {
        assertNotEmpty(locations, () -> "the locations of directive should not be empty.");
        ArrayList<DirectiveLocation> result = new ArrayList<>();
        for (Object location : locations) {
            DirectiveLocation directiveLocation = DirectiveLocation.newDirectiveLocation().name(location.toString()).build();
            result.add(directiveLocation);
        }
        return result;
    }

    private TypeDefinition createTypeDefinition(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        String name = (String) type.get("name");
        if (name.startsWith("__")) {
            return null;
        }
        switch (kind) {
            case "INTERFACE":
                return createInterface(type);
            case "OBJECT":
                return createObject(type);
            case "UNION":
                return createUnion(type);
            case "ENUM":
                return createEnum(type);
            case "INPUT_OBJECT":
                return createInputObject(type);
            case "SCALAR":
                return createScalar(type);
            default:
                return assertShouldNeverHappen("unexpected kind %s", kind);
        }
    }

    private TypeDefinition createScalar(Map<String, Object> input) {
        String name = (String) input.get("name");
        if (ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return null;
        }
        return ScalarTypeDefinition.newScalarTypeDefinition()
                .name(name)
                .description(toDescription(input))
                .build();
    }


    @SuppressWarnings("unchecked")
    UnionTypeDefinition createUnion(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("UNION"), () -> "wrong input");

        UnionTypeDefinition.Builder unionTypeDefinition = UnionTypeDefinition.newUnionTypeDefinition();
        unionTypeDefinition.name((String) input.get("name"));
        unionTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> possibleTypes = (List<Map<String, Object>>) input.get("possibleTypes");

        for (Map<String, Object> possibleType : possibleTypes) {
            TypeName typeName = TypeName.newTypeName().name((String) possibleType.get("name")).build();
            unionTypeDefinition.memberType(typeName);
        }

        return unionTypeDefinition.build();
    }

    @SuppressWarnings("unchecked")
    EnumTypeDefinition createEnum(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("ENUM"), () -> "wrong input");

        EnumTypeDefinition.Builder enumTypeDefinition = EnumTypeDefinition.newEnumTypeDefinition().name((String) input.get("name"));
        enumTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> enumValues = (List<Map<String, Object>>) input.get("enumValues");

        for (Map<String, Object> enumValue : enumValues) {

            EnumValueDefinition.Builder enumValueDefinition = EnumValueDefinition.newEnumValueDefinition().name((String) enumValue.get("name"));
            enumValueDefinition.description(toDescription(enumValue));

            createDeprecatedDirective(enumValue, enumValueDefinition);

            enumTypeDefinition.enumValueDefinition(enumValueDefinition.build());
        }

        return enumTypeDefinition.build();
    }

    @SuppressWarnings("unchecked")
    InterfaceTypeDefinition createInterface(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INTERFACE"), () -> "wrong input");

        InterfaceTypeDefinition.Builder interfaceTypeDefinition = InterfaceTypeDefinition.newInterfaceTypeDefinition().name((String) input.get("name"));
        interfaceTypeDefinition.description(toDescription(input));
        if (input.containsKey("interfaces") && input.get("interfaces") != null) {
            interfaceTypeDefinition.implementz(
                    map(
                            (List<Map<String, Object>>) input.get("interfaces"),
                            this::createTypeIndirection
                    )
            );
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");
        interfaceTypeDefinition.definitions(createFields(fields));

        return interfaceTypeDefinition.build();

    }

    @SuppressWarnings("unchecked")
    InputObjectTypeDefinition createInputObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INPUT_OBJECT"), () -> "wrong input");

        InputObjectTypeDefinition.Builder inputObjectTypeDefinition = InputObjectTypeDefinition.newInputObjectDefinition()
                .name((String) input.get("name"))
                .description(toDescription(input));

        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("inputFields");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(fields);
        inputObjectTypeDefinition.inputValueDefinitions(inputValueDefinitions);

        return inputObjectTypeDefinition.build();
    }

    @SuppressWarnings("unchecked")
    ObjectTypeDefinition createObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("OBJECT"), () -> "wrong input");

        ObjectTypeDefinition.Builder objectTypeDefinition = ObjectTypeDefinition.newObjectTypeDefinition().name((String) input.get("name"));
        objectTypeDefinition.description(toDescription(input));
        if (input.containsKey("interfaces")) {
            objectTypeDefinition.implementz(
                    map((List<Map<String, Object>>) input.get("interfaces"), this::createTypeIndirection)
            );
        }
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");

        objectTypeDefinition.fieldDefinitions(createFields(fields));

        return objectTypeDefinition.build();
    }

    private List<FieldDefinition> createFields(List<Map<String, Object>> fields) {
        List<FieldDefinition> result = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            FieldDefinition.Builder fieldDefinition = FieldDefinition.newFieldDefinition().name((String) field.get("name"));
            fieldDefinition.description(toDescription(field));
            fieldDefinition.type(createTypeIndirection((Map<String, Object>) field.get("type")));

            createDeprecatedDirective(field, fieldDefinition);

            List<Map<String, Object>> args = (List<Map<String, Object>>) field.get("args");
            List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
            fieldDefinition.inputValueDefinitions(inputValueDefinitions);
            result.add(fieldDefinition.build());
        }
        return result;
    }

    private void createDeprecatedDirective(Map<String, Object> field, NodeDirectivesBuilder nodeDirectivesBuilder) {
        List<Directive> directives = new ArrayList<>();
        if (Boolean.TRUE.equals(field.get("isDeprecated"))) {
            String reason = (String) field.get("deprecationReason");
            if (reason == null) {
                reason = "No longer supported"; // default according to spec
            }
            Argument reasonArg = Argument.newArgument().name("reason").value(StringValue.newStringValue().value(reason).build()).build();
            Directive deprecated = Directive.newDirective().name("deprecated").arguments(Collections.singletonList(reasonArg)).build();
            directives.add(deprecated);
        }
        nodeDirectivesBuilder.directives(directives);
    }

    @SuppressWarnings("unchecked")
    private List<InputValueDefinition> createInputValueDefinitions(List<Map<String, Object>> args) {
        List<InputValueDefinition> result = new ArrayList<>();
        for (Map<String, Object> arg : args) {
            Type argType = createTypeIndirection((Map<String, Object>) arg.get("type"));
            InputValueDefinition.Builder inputValueDefinition = InputValueDefinition.newInputValueDefinition().name((String) arg.get("name")).type(argType);
            inputValueDefinition.description(toDescription(arg));

            String valueLiteral = (String) arg.get("defaultValue");
            if (valueLiteral != null) {
                Value<?> value = Parser.parseValue(valueLiteral);
                inputValueDefinition.defaultValue(value);
            }
            result.add(inputValueDefinition.build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Type createTypeIndirection(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        switch (kind) {
            case "INTERFACE":
            case "OBJECT":
            case "UNION":
            case "ENUM":
            case "INPUT_OBJECT":
            case "SCALAR":
                return TypeName.newTypeName().name((String) type.get("name")).build();
            case "NON_NULL":
                return NonNullType.newNonNullType().type(createTypeIndirection((Map<String, Object>) type.get("ofType"))).build();
            case "LIST":
                return ListType.newListType().type(createTypeIndirection((Map<String, Object>) type.get("ofType"))).build();
            default:
                return assertShouldNeverHappen("Unknown kind %s", kind);
        }
    }

    private Description toDescription(Map<String, Object> input) {
        String description = (String) input.get("description");
        if (description == null) {
            return null;
        }

        String[] lines = description.split("\n");
        if (lines.length > 1) {
            return new Description(description, null, true);
        } else {
            return new Description(description, null, false);
        }
    }


}
