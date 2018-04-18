package graphql.schema2;

import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputObjectTypeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

/**
 * graphql clearly delineates between the types of objects that represent the output of a query and input objects that
 * can be fed into a graphql mutation.  You can define objects as input to graphql via this class
 *
 * See http://graphql.org/learn/schema/#input-types for more details on the concept
 */
@PublicApi
public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLInputFieldsContainer {

    private final String name;
    private final String description;
    private final Map<String, GraphQLInputObjectField> fieldMap = new LinkedHashMap<>();
    private final InputObjectTypeDefinition definition;

    @Internal
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
        this(name, description, fields, null);
    }

    @Internal
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields, InputObjectTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(fields, "fields can't be null");
        this.name = name;
        this.description = description;
        this.definition = definition;
        buildMap(fields);
    }

    private void buildMap(List<GraphQLInputObjectField> fields) {
        for (GraphQLInputObjectField field : fields) {
            String name = field.getName();
            if (fieldMap.containsKey(name))
                throw new AssertException("field " + name + " redefined");
            fieldMap.put(name, field);
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<GraphQLInputObjectField> getFields() {
        return new ArrayList<>(fieldMap.values());
    }

    public GraphQLInputObjectField getField(String name) {
        return fieldMap.get(name);
    }


    @Override
    public GraphQLInputObjectField getFieldDefinition(String name) {
        return fieldMap.get(name);
    }

    @Override
    public List<GraphQLInputObjectField> getFieldDefinitions() {
        return new ArrayList<>(fieldMap.values());
    }

    public InputObjectTypeDefinition getDefinition() {
        return definition;
    }

}
