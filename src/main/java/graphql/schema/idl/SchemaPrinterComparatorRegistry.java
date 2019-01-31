package graphql.schema.idl;

import graphql.schema.GraphQLType;

import java.util.Comparator;

public interface SchemaPrinterComparatorRegistry {

    /**
     * @return The registered {@code Comparator} or {@code null} if not found.
     */
    <T extends GraphQLType> Comparator<? super T> getComparator(SchemaPrinterComparatorEnvironment environment);
}
