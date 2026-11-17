package graphql.schema.idl;

import graphql.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Provides the Java runtime value for each graphql Enum value. Used for IDL driven schema creation.
 * <p>
 * Enum values are considered static: This is called when a schema is created. It is not used when a query is executed.
 */
@NullMarked
@PublicSpi
public interface EnumValuesProvider {

    /**
     * @param name an Enum value
     *
     * @return the runtime value, or null if no value is available; schema construction requires a non-null value for each declared enum value
     */
    @Nullable Object getValue(String name);

}
