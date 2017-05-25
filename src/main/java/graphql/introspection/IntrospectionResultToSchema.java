package graphql.introspection;

import graphql.language.Comment;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.TypeName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;

public class IntrospectionResultToSchema {


    public String introscpectionResultToSchema(Map<String, Object> introspectionResult) {
        return null;
    }

    private List<Comment> toComment(String description) {
        if (description == null) return Collections.emptyList();
        Comment comment = new Comment(description, new SourceLocation(1, 1));
        return Arrays.asList(comment);
    }

    /*
      type QueryType {
          hero(episode: Episode): Character
          human(id : String) : Human
          droid(id: ID!): Droid
      }
     */
    @SuppressWarnings("unchecked")
    public ObjectTypeDefinition createObject(Map<String, Object> input) {
        assertTrue(input.get("kind").equals("OBJECT"), "wrong input");

        ObjectTypeDefinition objectTypeDefinition = new ObjectTypeDefinition((String) input.get("name"));
        objectTypeDefinition.setComments(toComment((String) input.get("description")));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) input.get("fields");

        for (Map<String, Object> field : fields) {
            FieldDefinition fieldDefinition = new FieldDefinition((String) field.get("name"));
            fieldDefinition.setComments(toComment((String) field.get("description")));
            fieldDefinition.setType(createType((Map<String, Object>) field.get("type")));

            List<Map<String, Object>> args = (List<Map<String, Object>>) field.get("args");

            for (Map<String, Object> arg : args) {
                TypeName typeName = createType((Map<String, Object>) arg.get("type"));
                InputValueDefinition inputValueDefinition = new InputValueDefinition((String) arg.get("name"), typeName);

                if (arg.get("defaultValue") != null) {
                    StringValue defaultValue = new StringValue((String) arg.get("defaultValue"));
                    inputValueDefinition.setDefaultValue(defaultValue);
                }
                fieldDefinition.getInputValueDefinitions().add(inputValueDefinition);
            }
            objectTypeDefinition.getFieldDefinitions().add(fieldDefinition);
        }
        return objectTypeDefinition;
    }

    TypeName createType(Map<String, Object> type) {
        String kind = (String) type.get("kind");
        switch (kind) {
            case "INTERFACE":
            case "OBJECT":
            case "UNION":
            case "ENUM":
                return new TypeName((String) type.get("name"));
            case "SCALAR":
                return new TypeName((String) type.get("name"));
            default:
                return assertShouldNeverHappen("Unknown kind " + kind);
        }
    }

}
