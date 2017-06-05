package graphql.introspection;

import graphql.PublicApi;
import graphql.language.Comment;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;

@PublicApi
public class IntrospectionResultToSchema {


    @SuppressWarnings("unchecked")
    public Document createSchemaDefinition(Map<String, Object> introspectionResult) {
        assertTrue(introspectionResult.get("__schema") != null, "__schema expected");
        Map<String, Object> schema = (Map<String, Object>) introspectionResult.get("__schema");


        Map<String, Object> queryType = (Map<String, Object>) schema.get("queryType");
        assertNotNull(queryType, "queryType expected");
        TypeName query = new TypeName((String) queryType.get("name"));
        boolean nonDefaultQueryName = !"Query".equals(query.getName());

        SchemaDefinition schemaDefinition = new SchemaDefinition();
        schemaDefinition.getOperationTypeDefinitions().add(new OperationTypeDefinition("query", query));

        Map<String, Object> mutationType = (Map<String, Object>) schema.get("mutationType");
        boolean nonDefaultMutationName = false;
        if (mutationType != null) {
            TypeName mutation = new TypeName((String) mutationType.get("name"));
            nonDefaultMutationName = !"Mutation".equals(mutation.getName());
            schemaDefinition.getOperationTypeDefinitions().add(new OperationTypeDefinition("mutation", mutation));
        }

        Map<String, Object> subscriptionType = (Map<String, Object>) schema.get("subscriptionType");
        boolean nonDefaultSubscriptionName = false;
        if (subscriptionType != null) {
            TypeName subscription = new TypeName((String) subscriptionType.get("name"));
            nonDefaultSubscriptionName = !"Subscription".equals(subscription.getName());
            schemaDefinition.getOperationTypeDefinitions().add(new OperationTypeDefinition("subscription", subscription));
        }

        Document document = new Document();
        if (nonDefaultQueryName || nonDefaultMutationName || nonDefaultSubscriptionName) {
            document.getDefinitions().add(schemaDefinition);
        }

        List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
        for (Map<String, Object> type : types) {
            TypeDefinition typeDefinition = createTypeDefinition(type);
            if (typeDefinition == null) continue;
            document.getDefinitions().add(typeDefinition);
        }

        return document;
    }

    private TypeDefinition createTypeDefinition(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        String name = (String) type.get("name");
        if (name.startsWith("__")) return null;
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
                // todo don't ignore all scalars
                return null;
            default:
                return assertShouldNeverHappen("unexpected kind " + kind);
        }
    }


    @SuppressWarnings("unchecked")
    UnionTypeDefinition createUnion(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("UNION"), "wrong input");

        UnionTypeDefinition unionTypeDefinition = new UnionTypeDefinition((String) input.get("name"));
        unionTypeDefinition.setComments(toComment((String) input.get("description")));

        List<Map<String, Object>> possibleTypes = (List<Map<String, Object>>) input.get("possibleTypes");

        for (Map<String, Object> possibleType : possibleTypes) {
            TypeName typeName = new TypeName((String) possibleType.get("name"));
            unionTypeDefinition.getMemberTypes().add(typeName);
        }

        return unionTypeDefinition;
    }

    @SuppressWarnings("unchecked")
    EnumTypeDefinition createEnum(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("ENUM"), "wrong input");

        EnumTypeDefinition enumTypeDefinition = new EnumTypeDefinition((String) input.get("name"));
        enumTypeDefinition.setComments(toComment((String) input.get("description")));

        List<Map<String, Object>> enumValues = (List<Map<String, Object>>) input.get("enumValues");

        for (Map<String, Object> enumValue : enumValues) {

            EnumValueDefinition enumValueDefinition = new EnumValueDefinition((String) enumValue.get("name"));
            enumValueDefinition.setComments(toComment((String) enumValue.get("description")));
            enumTypeDefinition.getEnumValueDefinitions().add(enumValueDefinition);
        }

        return enumTypeDefinition;
    }

    @SuppressWarnings("unchecked")
    InterfaceTypeDefinition createInterface(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INTERFACE"), "wrong input");

        InterfaceTypeDefinition interfaceTypeDefinition = new InterfaceTypeDefinition((String) input.get("name"));
        interfaceTypeDefinition.setComments(toComment((String) input.get("description")));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");
        interfaceTypeDefinition.getFieldDefinitions().addAll(createFields(fields));

        return interfaceTypeDefinition;

    }

    @SuppressWarnings("unchecked")
    InputObjectTypeDefinition createInputObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("INPUT_OBJECT"), "wrong input");

        InputObjectTypeDefinition inputObjectTypeDefinition = new InputObjectTypeDefinition((String) input.get("name"));
        inputObjectTypeDefinition.setComments(toComment((String) input.get("description")));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("inputFields");
        List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(fields);
        inputObjectTypeDefinition.getInputValueDefinitions().addAll(inputValueDefinitions);

        return inputObjectTypeDefinition;
    }

    @SuppressWarnings("unchecked")
    ObjectTypeDefinition createObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("OBJECT"), "wrong input");

        ObjectTypeDefinition objectTypeDefinition = new ObjectTypeDefinition((String) input.get("name"));
        objectTypeDefinition.setComments(toComment((String) input.get("description")));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");

        objectTypeDefinition.getFieldDefinitions().addAll(createFields(fields));

        return objectTypeDefinition;
    }

    private List<FieldDefinition> createFields(List<Map<String, Object>> fields) {
        List<FieldDefinition> result = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            FieldDefinition fieldDefinition = new FieldDefinition((String) field.get("name"));
            fieldDefinition.setComments(toComment((String) field.get("description")));
            fieldDefinition.setType(createTypeIndirection((Map<String, Object>) field.get("type")));

            List<Map<String, Object>> args = (List<Map<String, Object>>) field.get("args");
            List<InputValueDefinition> inputValueDefinitions = createInputValueDefinitions(args);
            fieldDefinition.getInputValueDefinitions().addAll(inputValueDefinitions);
            result.add(fieldDefinition);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<InputValueDefinition> createInputValueDefinitions(List<Map<String, Object>> args) {
        List<InputValueDefinition> result = new ArrayList<>();
        for (Map<String, Object> arg : args) {
            Type argType = createTypeIndirection((Map<String, Object>) arg.get("type"));
            InputValueDefinition inputValueDefinition = new InputValueDefinition((String) arg.get("name"), argType);
            inputValueDefinition.setComments(toComment((String) arg.get("description")));

            if (arg.get("defaultValue") != null) {
                StringValue defaultValue = new StringValue((String) arg.get("defaultValue"));
                inputValueDefinition.setDefaultValue(defaultValue);
            }
            result.add(inputValueDefinition);
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
                return new TypeName((String) type.get("name"));
            case "NON_NULL":
                return new NonNullType(createTypeIndirection((Map<String, Object>) type.get("ofType")));
            case "LIST":
                return new ListType(createTypeIndirection((Map<String, Object>) type.get("ofType")));
            default:
                return assertShouldNeverHappen("Unknown kind " + kind);
        }
    }

    private List<Comment> toComment(String description) {
        if (description == null) return Collections.emptyList();
        Comment comment = new Comment(description, new SourceLocation(1, 1));
        return Arrays.asList(comment);
    }

}
