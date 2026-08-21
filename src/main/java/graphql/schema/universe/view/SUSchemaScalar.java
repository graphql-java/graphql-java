package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.Coercing;
import graphql.schema.SchemaAppliedDirective;
import graphql.schema.SchemaAppliedDirectiveArgument;
import graphql.schema.SchemaScalar;
import graphql.schema.universe.SUScalarType;
import org.jspecify.annotations.Nullable;

@Internal
public final class SUSchemaScalar
        extends AbstractSUSchemaNamedType implements SchemaScalar {

    @Internal
    public SUSchemaScalar(
            SUExecutableSchema executableSchema,
            SUScalarType type) {
        super(executableSchema, type);
    }

    @Override
    public Coercing<?, ?> getCoercing() {
        return getExecutableSchema().getScalarCoercing(this);
    }

    @Override
    public @Nullable String getSpecifiedByUrl() {
        for (SchemaAppliedDirective directive :
                getExecutableSchema().getAppliedDirectives(this)) {
            if (!"specifiedBy".equals(directive.getName())) {
                continue;
            }
            SchemaAppliedDirectiveArgument url = directive.getArgument("url");
            if (url == null || url.getArgumentValue().isNotSet()) {
                continue;
            }
            Object value = url.getArgumentValue().getValue();
            if (value instanceof graphql.language.StringValue) {
                return ((graphql.language.StringValue) value).getValue();
            }
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
