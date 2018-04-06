package graphql.execution;


import graphql.Internal;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 *
 * Fields with '@skip' and/or '@include' directive will be resolved to be included, or no.  If included, only fields WITHOUT the
 * '@defer' directive will be collected.
 */
@Internal
public class NonDeferredFieldCollectorFactory {

    private static final FieldNodeFilter fieldNodeFilter = new NonDeferredFieldNodeFilter();
    private static final FieldCollector fieldCollector = new SimpleFieldCollector(fieldNodeFilter);

    public static FieldCollector nonDeferredFieldCollector() {
        return fieldCollector;
    }

}
