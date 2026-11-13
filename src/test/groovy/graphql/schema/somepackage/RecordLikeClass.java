package graphql.schema.somepackage;

import graphql.schema.DataFetchingEnvironment;

/**
 * This is obviously not an actual record class from Java 14 onwards, but it
 * smells like one and that's enough really.  Its public, not derived from another
 * class and has a public method named after a property
 */
public class RecordLikeClass {

    public String recordProperty() {
        return "recordProperty";
    }

    public String recordArgumentMethod(DataFetchingEnvironment environment) {
        return "recordArgumentMethod";
    }

    @Override
    public int hashCode() {
        return 666;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "toString";
    }
}
