package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLInputObjectType implements GraphQLType, GraphQLInputType {

    private final String name;


    private final List<GraphQLInputObjectField> fields = new ArrayList<>();

    public GraphQLInputObjectType(String name, List<GraphQLInputObjectField> fields) {
        this.name = name;
        this.fields.addAll(fields);
    }

    public String getName() {
        return name;
    }

    public List<GraphQLInputObjectField> getFields() {
        return fields;
    }
}
