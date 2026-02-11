package graphql.schema;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static graphql.Assert.assertNotNull;

/**
 * A modified type that indicates there is a list of the underlying wrapped type, eg a list of strings or a list of booleans.
 * <p>
 * See <a href="https://graphql.org/learn/schema/#lists-and-non-null">https://graphql.org/learn/schema/#lists-and-non-null</a> for more details on the concept
 */
@PublicApi
@NullMarked
public class GraphQLList implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLModifiedType, GraphQLNullableType {

    private final GraphQLType originalWrappedType;
    private @Nullable GraphQLType replacedWrappedType;

    public static final String CHILD_WRAPPED_TYPE = "wrappedType";

    /**
     * A factory method for creating list types so that when used with static imports allows
     * more readable code such as
     * {@code .type(list(GraphQLString)) }
     *
     * @param wrappedType the type to wrap as being a list
     *
     * @return a GraphQLList of that wrapped type
     */
    public static GraphQLList list(GraphQLType wrappedType) {
        return new GraphQLList(wrappedType);
    }


    public GraphQLList(GraphQLType wrappedType) {
        assertNotNull(wrappedType, "wrappedType can't be null");
        this.originalWrappedType = wrappedType;
    }


    @Override
    public GraphQLType getWrappedType() {
        return replacedWrappedType != null ? replacedWrappedType : originalWrappedType;
    }

    public GraphQLType getOriginalWrappedType() {
        return originalWrappedType;
    }

    void replaceType(GraphQLType type) {
        this.replacedWrappedType = type;
    }


    public boolean isEqualTo(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GraphQLList that = (GraphQLList) o;
        GraphQLType wrappedType = getWrappedType();
        if (wrappedType instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) wrappedType).isEqualTo(that.getWrappedType());
        }
        return Objects.equals(wrappedType, that.getWrappedType());
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLList(this, context);
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
        return list(newChildren.getChildOrNull(CHILD_WRAPPED_TYPE));
    }

    @Override
    public GraphQLSchemaElement copy() {
        return new GraphQLList(originalWrappedType);
    }


    @Override
    public String toString() {
        return GraphQLTypeUtil.simplePrint(this);
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
