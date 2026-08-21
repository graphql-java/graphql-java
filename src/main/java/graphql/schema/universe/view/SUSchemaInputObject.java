package graphql.schema.universe.view;

import graphql.Directives;
import graphql.Internal;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.universe.SUInputField;
import graphql.schema.universe.SUInputObjectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public final class SUSchemaInputObject
        extends AbstractSUSchemaNamedType implements SchemaInputObject {

    @Internal
    public SUSchemaInputObject(
            SUExecutableSchema executableSchema,
            SUInputObjectType type) {
        super(executableSchema, type);
    }

    @Override
    public boolean isOneOf() {
        return !getExecutableSchema()
                .getSchema()
                .getAppliedDirectives(
                        getInputObjectTypeVertex(),
                        Directives.OneOfDirective.getName())
                .isEmpty();
    }

    @Override
    public List<? extends SchemaInputField> getFieldDefinitions() {
        List<SUInputField> fields = getExecutableSchema()
                .getSchema()
                .getInputFields(getInputObjectTypeVertex());
        List<SchemaInputField> result =
                new ArrayList<>(fields.size());
        for (SUInputField field : fields) {
            result.add(new SUSchemaInputField(
                    getExecutableSchema(),
                    field));
        }
        return Collections.unmodifiableList(result);
    }

    @Internal
    public SUInputObjectType getInputObjectTypeVertex() {
        return (SUInputObjectType) getTypeVertex();
    }
}
