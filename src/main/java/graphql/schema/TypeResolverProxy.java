package graphql.schema;


/**
 * <p>TypeResolverProxy class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class TypeResolverProxy implements TypeResolver {

    private TypeResolver typeResolver;

    /**
     * <p>Getter for the field <code>typeResolver</code>.</p>
     *
     * @return a {@link graphql.schema.TypeResolver} object.
     */
    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    /**
     * <p>Setter for the field <code>typeResolver</code>.</p>
     *
     * @param typeResolver a {@link graphql.schema.TypeResolver} object.
     */
    public void setTypeResolver(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    /** {@inheritDoc} */
    @Override
    public GraphQLObjectType getType(Object object) {
        return typeResolver != null ? typeResolver.getType(object) : null;
    }
}
