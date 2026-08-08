package graphql.execution;


import graphql.Internal;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.ExecutableSchema;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaList;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;

import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;

@Internal
public class TypeFromAST {


    public static GraphQLType getTypeFromAST(GraphQLSchema schema, Type type) {
        GraphQLType innerType;
        if (type instanceof ListType) {
            innerType = getTypeFromAST(schema, ((ListType) type).getType());
            return list(innerType);
        } else if (type instanceof NonNullType) {
            innerType = getTypeFromAST(schema, ((NonNullType) type).getType());
            return nonNull(innerType);
        }

        return schema.getType(((TypeName) type).getName());
    }

    public static SchemaType getSchemaTypeFromAST(
            ExecutableSchema schema,
            Type<?> type) {
        if (type instanceof ListType) {
            SchemaType wrappedType = getSchemaTypeFromAST(
                    schema,
                    ((ListType) type).getType());
            return wrappedType == null
                    ? null
                    : (SchemaList) () -> wrappedType;
        }
        if (type instanceof NonNullType) {
            SchemaType wrappedType = getSchemaTypeFromAST(
                    schema,
                    ((NonNullType) type).getType());
            return wrappedType == null
                    ? null
                    : (SchemaNonNull) () -> wrappedType;
        }
        return schema.getType(((TypeName) type).getName());
    }
}
