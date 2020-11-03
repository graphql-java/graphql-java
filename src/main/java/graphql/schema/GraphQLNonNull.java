package graphql.schema;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * A modified type that indicates there the underlying wrapped type will not be null.
 * <p>
 * See http://graphql.org/learn/schema/#lists-and-non-null for more details on the concept
 */
@PublicApi
public class GraphQLNonNull implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType {

    /**
     * A factory method for creating non null types so that when used with static imports allows
     * more readable code such as
     * {@code .type(nonNull(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being non null
     *
     * @return a GraphQLNonNull of that wrapped type
     */
    public static GraphQLNonNull nonNull(GraphQLType wrappedType) {
        return new GraphQLNonNull(wrappedType);
    }

    private final GraphQLType originalWrappedType;
    private GraphQLType replacedWrappedType;

    public static final String CHILD_WRAPPED_TYPE = "wrappedType";


    public GraphQLNonNull(GraphQLType wrappedType) {
        assertNotNull(wrappedType, () -> "wrappedType can't be null");
        assertNonNullWrapping(wrappedType);
        this.originalWrappedType = wrappedType;
    }

    private void assertNonNullWrapping(GraphQLType wrappedType) {
        assertTrue(!GraphQLTypeUtil.isNonNull(wrappedType), () ->
                String.format("A non null type cannot wrap an existing non null type '%s'", GraphQLTypeUtil.simplePrint(wrappedType)));
    }

    @Override
    public GraphQLType getWrappedType() {
        return replacedWrappedType != null ? replacedWrappedType : originalWrappedType;
    }

    public GraphQLType getOriginalWrappedType() {
        return originalWrappedType;
    }

    void replaceType(GraphQLType type) {
        assertNonNullWrapping(type);
        this.replacedWrappedType = type;
    }

    public boolean isEqualTo(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GraphQLNonNull that = (GraphQLNonNull) o;
        GraphQLType wrappedType = getWrappedType();
        if (wrappedType instanceof GraphQLList) {
            return ((GraphQLList) wrappedType).isEqualTo(that.getWrappedType());
        }
        return Objects.equals(wrappedType, that.getWrappedType());
    }


    @Override
    public String toString() {
        return GraphQLTypeUtil.simplePrint(this);
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLNonNull(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        return Collections.singletonList(getWrappedType());
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .child(CHILD_WRAPPED_TYPE, originalWrappedType)
                .build();
    }

    @Override
    public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return nonNull(newChildren.getChildOrNull(CHILD_WRAPPED_TYPE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

}
