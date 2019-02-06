package graphql.schema.idl;

import graphql.schema.GraphQLType;

import java.util.Comparator;

public interface SchemaPrinterComparatorRegistry {

    /**
     * @param environment Defines the scope to control where the {@code Comparator} can be applied.
     * @param <T>         the type of the comparator
     *
     * @return The registered {@code Comparator} or {@code null} if not found.
     */
    <T extends GraphQLType> Comparator<? super T> getComparator(SchemaPrinterComparatorEnvironment environment);
}
