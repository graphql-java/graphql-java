package graphql.execution;


import graphql.Internal;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

@Internal
public class TypeFromAST {


    public static GraphQLType getTypeFromAST(GraphQLSchema schema, Type type) {
        GraphQLType innerType;
        if (type instanceof ListType) {
            innerType = getTypeFromAST(schema, ((ListType) type).getType());
            return innerType != null ? new GraphQLList(innerType) : null;
        } else if (type instanceof NonNullType) {
            innerType = getTypeFromAST(schema, ((NonNullType) type).getType());
            return innerType != null ? new GraphQLNonNull(innerType) : null;
        }

        return schema.getType(((TypeName) type).getName());
    }
}
