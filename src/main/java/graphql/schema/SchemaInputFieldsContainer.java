package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A named type that declares input fields.
 */
@ExperimentalApi
@NullMarked
public interface SchemaInputFieldsContainer extends SchemaNamedType {

    /**
     * Returns all input fields declared by this type without applying schema-level visibility.
     *
     * @return the declared input field definitions
     */
    List<? extends SchemaInputField> getFieldDefinitions();
}
