package graphql.introspection;

import graphql.Assert;
import graphql.language.Comment;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SourceLocation;
import graphql.language.TypeName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    public ObjectTypeDefinition createObject(Map<String, Object> input) {
        Assert.assertTrue(input.get("kind").equals("OBJECT"), "wrong input");
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
                fieldDefinition.getInputValueDefinitions().add(inputValueDefinition);
            }
            objectTypeDefinition.getFieldDefinitions().add(fieldDefinition);
        }
        return objectTypeDefinition;
    }

    TypeName createType(Map<String, Object> type) {
        TypeName typeName = new TypeName((String) type.get("name"));
        return typeName;
    }
}
