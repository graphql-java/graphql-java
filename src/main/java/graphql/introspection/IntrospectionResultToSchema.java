package graphql.introspection;

import graphql.ExecutionResult;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
import static graphql.Directives.isBuiltInDirective;

@SuppressWarnings("unchecked")
@PublicApi
@NullMarked
public class IntrospectionResultToSchema {

    /**
     * Returns a IDL Document that represents the schema as defined by the introspection execution result
     *
     * @param introspectionResult the result of an introspection query on a schema
     *
     * @return a IDL Document of the schema
     */
    public @Nullable Document createSchemaDefinition(ExecutionResult introspectionResult) {
        if (!introspectionResult.isDataPresent()) {
            return null;
        }

        Map<String, Object> introspectionResultMap = assertNotNull(introspectionResult.getData(), "data expected");
        return createSchemaDefinition(introspectionResultMap);
    }


    /**
     * Returns a IDL Document that represents the schema as defined by the introspection result map
     *
     * @param introspectionResult the result of an introspection query on a schema
     *
     * @return a IDL Document of the schema
     */
    public Document createSchemaDefinition(Map<String, Object> introspectionResult) {
        Map<String, Object> schema = assertNotNull((Map<String, Object>) introspectionResult.get("__schema"), "__schema expected");

        Map<String, Object> queryType = assertNotNull((Map<String, Object>) schema.get("queryType"), "queryType expected");
        TypeName query = TypeName.newTypeName().name(assertNotNull((String) queryType.get("name"), "query name expected")).build();
        boolean nonDefaultQueryName = !"Query".equals(query.getName());

        SchemaDefinition.Builder schemaDefinition = SchemaDefinition.newSchemaDefinition();
        schemaDefinition.description(toDescription(schema));
        schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("query").typeName(query).build());

        Map<String, Object> mutationType = (Map<String, Object>) schema.get("mutationType");
        boolean nonDefaultMutationName = false;
        if (mutationType != null) {
            TypeName mutation = TypeName.newTypeName().name(assertNotNull((String) mutationType.get("name"), "mutation name expected")).build();
            nonDefaultMutationName = !"Mutation".equals(mutation.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("mutation").typeName(mutation).build());
        }

        Map<String, Object> subscriptionType = (Map<String, Object>) schema.get("subscriptionType");
        boolean nonDefaultSubscriptionName = false;
        if (subscriptionType != null) {
            TypeName subscription = TypeName.newTypeName().name(assertNotNull((String) subscriptionType.get("name"), "subscription name expected")).build();
            nonDefaultSubscriptionName = !"Subscription".equals(subscription.getName());
            schemaDefinition.operationTypeDefinition(OperationTypeDefinition.newOperationTypeDefinition().name("subscription").typeName(subscription).build());
        }

        Document.Builder document = Document.newDocument();
        if (nonDefaultQueryName || nonDefaultMutationName || nonDefaultSubscriptionName) {
            document.definition(schemaDefinition.build());
        }

        List<Map<String, Object>> types = assertNotNull((List<Map<String, Object>>) schema.get("types"), "types expected");
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

    private @Nullable DirectiveDefinition createDirective(Map<String, Object> input) {
        String directiveName = assertNotNull((String) input.get("name"), "directive name expected");
        if (isBuiltInDirective(directiveName)) {
            return null;
        }

        DirectiveDefinition.Builder directiveDefBuilder = DirectiveDefinition.newDirectiveDefinition();
        directiveDefBuilder
                .name(directiveName)
                .description(toDescription(input));

        List<Object> locations = assertNotNull((List<Object>) input.get("locations"), "locations expected");
        List<DirectiveLocation> directiveLocations = createDirectiveLocations(locations);
        directiveDefBuilder.directiveLocations(directiveLocations);


        List<Map<String, Object>> args = assertNotNull((List<Map<String, Object>>) input.get("args"), "args expected");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
        directiveDefBuilder.inputValueDefinitions(inputValueDefinitions);
        Optional.ofNullable((Boolean) input.get("isRepeatable")).ifPresent(value -> directiveDefBuilder.repeatable(value));

        return directiveDefBuilder.build();
    }

    private List<DirectiveLocation> createDirectiveLocations(List<Object> locations) {
        assertNotEmpty(locations, "the locations of directive should not be empty.");
        List<DirectiveLocation> result = new ArrayList<>(locations.size());
        for (Object location : locations) {
            DirectiveLocation directiveLocation = DirectiveLocation.newDirectiveLocation().name(location.toString()).build();
            result.add(directiveLocation);
        }
        return result;
    }

    private @Nullable TypeDefinition createTypeDefinition(Map<String, Object> type) {
        String kind = assertNotNull((String) type.get("kind"), "kind expected");
        String name = assertNotNull((String) type.get("name"), "name expected");
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

    private @Nullable TypeDefinition createScalar(Map<String, Object> input) {
        String name = assertNotNull((String) input.get("name"), "name expected");
        if (ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return null;
        }
        return ScalarTypeDefinition.newScalarTypeDefinition()
                .name(name)
                .description(toDescription(input))
                .build();
    }


    UnionTypeDefinition createUnion(Map<String, Object> input) {
        assertTrue("UNION".equals(input.get("kind")), "wrong input");

        UnionTypeDefinition.Builder unionTypeDefinition = UnionTypeDefinition.newUnionTypeDefinition();
        unionTypeDefinition.name(assertNotNull((String) input.get("name"), "name expected"));
        unionTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> possibleTypes = assertNotNull((List<Map<String, Object>>) input.get("possibleTypes"), "possibleTypes expected");

        for (Map<String, Object> possibleType : possibleTypes) {
            TypeName typeName = TypeName.newTypeName().name(assertNotNull((String) possibleType.get("name"), "possibleType name expected")).build();
            unionTypeDefinition.memberType(typeName);
        }

        return unionTypeDefinition.build();
    }

    EnumTypeDefinition createEnum(Map<String, Object> input) {
        assertTrue("ENUM".equals(input.get("kind")), "wrong input");

        EnumTypeDefinition.Builder enumTypeDefinition = EnumTypeDefinition.newEnumTypeDefinition().name(assertNotNull((String) input.get("name"), "name expected"));
        enumTypeDefinition.description(toDescription(input));

        List<Map<String, Object>> enumValues = assertNotNull((List<Map<String, Object>>) input.get("enumValues"), "enumValues expected");

        for (Map<String, Object> enumValue : enumValues) {

            EnumValueDefinition.Builder enumValueDefinition = EnumValueDefinition.newEnumValueDefinition().name(assertNotNull((String) enumValue.get("name"), "enumValue name expected"));
            enumValueDefinition.description(toDescription(enumValue));

            createDeprecatedDirective(enumValue, enumValueDefinition);

            enumTypeDefinition.enumValueDefinition(enumValueDefinition.build());
        }

        return enumTypeDefinition.build();
    }

    InterfaceTypeDefinition createInterface(Map<String, Object> input) {
        assertTrue("INTERFACE".equals(input.get("kind")), "wrong input");

        InterfaceTypeDefinition.Builder interfaceTypeDefinition = InterfaceTypeDefinition.newInterfaceTypeDefinition().name(assertNotNull((String) input.get("name"), "name expected"));
        interfaceTypeDefinition.description(toDescription(input));
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) input.get("interfaces");
        if (interfaces != null) {
            interfaceTypeDefinition.implementz(
                    map(interfaces, this::createTypeIndirection)
            );
        }
        List<Map<String, Object>> fields = assertNotNull((List<Map<String, Object>>) input.get("fields"), "fields expected");
        interfaceTypeDefinition.definitions(createFields(fields));

        return interfaceTypeDefinition.build();

    }

    InputObjectTypeDefinition createInputObject(Map<String, Object> input) {
        assertTrue("INPUT_OBJECT".equals(input.get("kind")), "wrong input");

        InputObjectTypeDefinition.Builder inputObjectTypeDefinition = InputObjectTypeDefinition.newInputObjectDefinition()
                .name(assertNotNull((String) input.get("name"), "name expected"))
                .description(toDescription(input));

        List<Map<String, Object>> fields = assertNotNull((List<Map<String, Object>>) input.get("inputFields"), "inputFields expected");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(fields);
        inputObjectTypeDefinition.inputValueDefinitions(inputValueDefinitions);

        return inputObjectTypeDefinition.build();
    }

    ObjectTypeDefinition createObject(Map<String, Object> input) {
        assertTrue("OBJECT".equals(input.get("kind")), "wrong input");

        ObjectTypeDefinition.Builder objectTypeDefinition = ObjectTypeDefinition.newObjectTypeDefinition().name(assertNotNull((String) input.get("name"), "name expected"));
        objectTypeDefinition.description(toDescription(input));
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) input.get("interfaces");
        if (interfaces != null) {
            objectTypeDefinition.implementz(
                    map(interfaces, this::createTypeIndirection)
            );
        }
        List<Map<String, Object>> fields = assertNotNull((List<Map<String, Object>>) input.get("fields"), "fields expected");

        objectTypeDefinition.fieldDefinitions(createFields(fields));

        return objectTypeDefinition.build();
    }

    private List<FieldDefinition> createFields(List<Map<String, Object>> fields) {
        List<FieldDefinition> result = new ArrayList<>(fields.size());
        for (Map<String, Object> field : fields) {
            FieldDefinition.Builder fieldDefinition = FieldDefinition.newFieldDefinition().name(assertNotNull((String) field.get("name"), "field name expected"));
            fieldDefinition.description(toDescription(field));
            fieldDefinition.type(createTypeIndirection(assertNotNull((Map<String, Object>) field.get("type"), "field type expected")));

            createDeprecatedDirective(field, fieldDefinition);

            List<Map<String, Object>> args = assertNotNull((List<Map<String, Object>>) field.get("args"), "field args expected");
            List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
            fieldDefinition.inputValueDefinitions(inputValueDefinitions);
            result.add(fieldDefinition.build());
        }
        return result;
    }

    private void createDeprecatedDirective(Map<String, Object> field, NodeDirectivesBuilder nodeDirectivesBuilder) {
        if (Boolean.TRUE.equals(field.get("isDeprecated"))) {
            String reason = (String) field.get("deprecationReason");
            if (reason == null) {
                reason = "No longer supported"; // default according to spec
            }
            Argument reasonArg = Argument.newArgument().name("reason").value(StringValue.newStringValue().value(reason).build()).build();
            Directive deprecated = Directive.newDirective().name("deprecated").arguments(Collections.singletonList(reasonArg)).build();
            nodeDirectivesBuilder.directive(deprecated);
        }
    }

    private List<InputValueDefinition> createInputValueDefinitions(List<Map<String, Object>> args) {
        List<InputValueDefinition> result = new ArrayList<>(args.size());
        for (Map<String, Object> arg : args) {
            Type argType = createTypeIndirection(assertNotNull((Map<String, Object>) arg.get("type"), "arg type expected"));
            InputValueDefinition.Builder inputValueDefinition = InputValueDefinition.newInputValueDefinition().name(assertNotNull((String) arg.get("name"), "arg name expected")).type(argType);
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

    private Type createTypeIndirection(Map<String, Object> type) {
        String kind = assertNotNull((String) type.get("kind"), "kind expected");
        switch (kind) {
            case "INTERFACE":
            case "OBJECT":
            case "UNION":
            case "ENUM":
            case "INPUT_OBJECT":
            case "SCALAR":
                return TypeName.newTypeName().name(assertNotNull((String) type.get("name"), "name expected")).build();
            case "NON_NULL":
                return NonNullType.newNonNullType().type(createTypeIndirection(assertNotNull((Map<String, Object>) type.get("ofType"), "ofType expected"))).build();
            case "LIST":
                return ListType.newListType().type(createTypeIndirection(assertNotNull((Map<String, Object>) type.get("ofType"), "ofType expected"))).build();
            default:
                return assertShouldNeverHappen("Unknown kind %s", kind);
        }
    }

    private @Nullable Description toDescription(Map<String, Object> input) {
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
