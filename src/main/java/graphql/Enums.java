package graphql;

import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;

/**
 * The enums that are understood by graphql-java
 */
public class Enums {
    public static final String ON_ERROR = "OnError";
    public static final String ON_ERROR_PROPAGATE = "PROPAGATE";
    public static final String ON_ERROR_NULL = "NULL";

    @ExperimentalApi
    public static final EnumTypeDefinition ON_ERROR_TYPE_DEFINITION;

    static {
        ON_ERROR_TYPE_DEFINITION = EnumTypeDefinition.newEnumTypeDefinition()
                .name(ON_ERROR)
                .enumValueDefinition(
                        EnumValueDefinition.newEnumValueDefinition()
                                .name(ON_ERROR_PROPAGATE)
                                .description(new Description("If the error happens in a non-nullable position, the error is propagated to the neareast nullable parent position", null, true))
                                .build()
                )
                .enumValueDefinition(
                        EnumValueDefinition.newEnumValueDefinition()
                                .name(ON_ERROR_NULL)
                                .description(new Description("Null is always returned regardless of whether the position is nullable or not", null, true))
                                .build()
                )
                .build();
    }
}
