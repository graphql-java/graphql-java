package graphql.execution;


import graphql.Internal;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.ExecutableSchema;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaType;

import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;

@Internal
public class TypeFromAST {


    public static GraphQLType getTypeFromAST(GraphQLSchema schema, Type type) {
        GraphQLType innerType;
        if (type instanceof ListType) {
            innerType = getTypeFromAST(schema, ((ListType) type).getType());
            return innerType != null ? list(innerType) : null;
        } else if (type instanceof NonNullType) {
            innerType = getTypeFromAST(schema, ((NonNullType) type).getType());
            return innerType != null ? nonNull(innerType) : null;
        }

        return schema.getType(((TypeName) type).getName());
    }

    public static SchemaType getSchemaTypeFromAST(
            ExecutableSchema schema,
            Type<?> type) {
        SchemaType innerType;
        if (type instanceof ListType) {
            innerType = getSchemaTypeFromAST(
                    schema,
                    ((ListType) type).getType());
            return innerType == null
                    ? null
                    : new ResolvedSchemaListType(innerType);
        }
        if (type instanceof NonNullType) {
            innerType = getSchemaTypeFromAST(
                    schema,
                    ((NonNullType) type).getType());
            return innerType == null
                    ? null
                    : new ResolvedSchemaNonNullType(innerType);
        }
        return schema.getType(((TypeName) type).getName());
    }
}
