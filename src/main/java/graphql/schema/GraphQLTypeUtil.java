package graphql.schema;

public class GraphQLTypeUtil {

    /**
     * This will get the unwrapped type name that includes the non null and list wrappers
     * so it might be '[typeName!]'
     *
     * @param type the type in play
     *
     * @return the unwrapped type name
     */
    public static String getUnwrappedTypeName(GraphQLType type) {
        StringBuilder sb = new StringBuilder();
        if (type instanceof GraphQLNonNull) {
            sb.append(getUnwrappedTypeName(((GraphQLNonNull) type).getWrappedType()));
            sb.append("!");
        } else if (type instanceof GraphQLList) {
            sb.append("[");
            sb.append(getUnwrappedTypeName(((GraphQLList) type).getWrappedType()));
            sb.append("]");
        } else {
            sb.append(type.getName());
        }
        return sb.toString();
    }
}
