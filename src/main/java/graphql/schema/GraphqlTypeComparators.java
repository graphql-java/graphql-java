package graphql.schema;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@PublicApi
public class GraphqlTypeComparators {

    /**
     * This sorts the list of {@link graphql.schema.GraphQLType} objects (by name) and allocates a new sorted
     * list back.
     *
     * @param types the types to sort
     * @param <T>   the type of type
     *
     * @return a new allocated list of sorted things
     */
    public static <T extends GraphQLType> List<T> sortGraphQLTypes(Collection<T> types) {
        ArrayList<T> sorted = new ArrayList<>(types);
        sorted.sort(graphQLTypeComparator());
        return sorted;
    }

    /**
     * Returns a comparator that compares {@link graphql.schema.GraphQLType} objects by ascending name
     *
     * @param <T> the type of type
     *
     * @return a comparator that compares {@link graphql.schema.GraphQLType} objects by ascending name
     */
    public static <T extends GraphQLType> Comparator<? super GraphQLType> graphQLTypeComparator() {
        return Comparator.comparing(GraphQLType::getName);
    }

}
