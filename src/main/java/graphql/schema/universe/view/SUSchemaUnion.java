package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaUnion;
import graphql.schema.universe.SUObjectType;
import graphql.schema.universe.SUUnionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public final class SUSchemaUnion
        extends AbstractSUSchemaNamedType implements SchemaUnion {

    @Internal
    public SUSchemaUnion(
            SUExecutableSchema executableSchema,
            SUUnionType type) {
        super(executableSchema, type);
    }

    @Override
    public List<SUSchemaObject> getTypes() {
        List<SUObjectType> types = getExecutableSchema()
                .getSchema()
                .getUnionMembers(getUnionTypeVertex());
        List<SUSchemaObject> result = new ArrayList<>(types.size());
        for (SUObjectType type : types) {
            result.add(new SUSchemaObject(getExecutableSchema(), type));
        }
        return Collections.unmodifiableList(result);
    }

    @Internal
    public SUUnionType getUnionTypeVertex() {
        return (SUUnionType) getTypeVertex();
    }
}
