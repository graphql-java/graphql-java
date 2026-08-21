package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A composite type that declares output fields.
 */
@ExperimentalApi
@NullMarked
public interface SchemaFieldsContainer extends SchemaComposite {

    /**
     * Returns all fields declared by this type without applying schema-level visibility.
     *
     * @return the declared field definitions
     */
    List<? extends SchemaField> getFieldDefinitions();
}
