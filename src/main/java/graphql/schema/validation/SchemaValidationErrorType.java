package graphql.schema.validation;

import graphql.Internal;

@Internal
public enum SchemaValidationErrorType {

    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces
}
