package graphql.schema;

import com.google.common.collect.ImmutableList;
import graphql.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Internal
public class GraphqlTypeComparators {

    /**
     * This sorts the list of {@link graphql.schema.GraphQLType} objects (by name) and allocates a new sorted
     * list back.
     *
     * @param <T>        the type of type
     * @param comparator the comparator to use
     * @param types      the types to sort
     *
     * @return a new allocated list of sorted things
     */
    public static <T extends GraphQLSchemaElement> List<T> sortTypes(Comparator<? super GraphQLSchemaElement> comparator, Collection<T> types) {
        List<T> sorted = new ArrayList<>(types);
        sorted.sort(comparator);
        return ImmutableList.copyOf(sorted);
    }

    /**
     * Returns a comparator that laves {@link graphql.schema.GraphQLType} objects as they are
     *
     * @return a comparator that laves {@link graphql.schema.GraphQLType} objects as they are
     */
    public static Comparator<? super GraphQLSchemaElement> asIsOrder() {
        return (o1, o2) -> 0;
    }

    /**
     * Returns a comparator that compares {@link graphql.schema.GraphQLType} objects by ascending name
     *
     * @return a comparator that compares {@link graphql.schema.GraphQLType} objects by ascending name
     */
    public static Comparator<? super GraphQLSchemaElement> byNameAsc() {
        return Comparator.comparing(graphQLSchemaElement -> ((GraphQLNamedSchemaElement) graphQLSchemaElement).getName());
    }

}
