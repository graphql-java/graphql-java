package graphql.language;


import graphql.PublicApi;

import java.util.List;

/**
 * A {@link TypeDefinition} that might implement interfaces
 *
 * @param <T>
 */
@PublicApi
public interface ImplementingTypeDefinition<T extends ImplementingTypeDefinition> extends TypeDefinition<T> {

    List<Type> getImplements();

    List<FieldDefinition> getFieldDefinitions();
}
