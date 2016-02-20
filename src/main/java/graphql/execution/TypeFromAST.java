package graphql.execution;


import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

/**
 * <p>TypeFromAST class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class TypeFromAST {


    /**
     * <p>getTypeFromAST.</p>
     *
     * @param schema a {@link graphql.schema.GraphQLSchema} object.
     * @param type a {@link graphql.language.Type} object.
     * @return a {@link graphql.schema.GraphQLType} object.
     */
    public static GraphQLType getTypeFromAST(GraphQLSchema schema, Type type) {
        if (type instanceof ListType) {
            return new GraphQLList(getTypeFromAST(schema, ((ListType) type).getType()));
        } else if (type instanceof NonNullType) {
            return new GraphQLNonNull(getTypeFromAST(schema, ((NonNullType) type).getType()));
        }
        return schema.getType(((TypeName) type).getName());
    }
}
