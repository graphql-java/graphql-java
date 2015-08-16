package graphql.schema;


public class TypeResolverProxy implements TypeResolver {

    private TypeResolver typeResolver;

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    public void setTypeResolver(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    @Override
    public GraphQLObjectType getType(Object object) {
        return typeResolver != null ? typeResolver.getType(object) : null;
    }
}
