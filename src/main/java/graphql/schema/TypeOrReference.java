package graphql.schema;

import static graphql.Assert.assertNotNull;

public class TypeOrReference<T extends GraphQLType> {
    private final T type;
    private final GraphQLTypeReference reference;

    public TypeOrReference(T type) {
        assertNotNull(type, "type is null");
        this.type = type;
        this.reference = null;
    }

    public TypeOrReference(GraphQLTypeReference reference) {
        assertNotNull(reference, "reference is null");
        this.type = null;
        this.reference = reference;
    }

    public T getType() {
        return type;
    }

    public GraphQLTypeReference getReference() {
        return reference;
    }

    public boolean isReference() {
        return reference != null;
    }

    public boolean isType() {
        return type != null;
    }

    public GraphQLType getTypeOrReference() {
        return isReference() ? reference : type;
    }
}