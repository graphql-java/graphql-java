package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaObject;
import graphql.schema.universe.SUInterfaceType;
import graphql.schema.universe.SUObjectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public final class SUSchemaObject
        extends AbstractSUSchemaNamedType implements SchemaObject {

    @Internal
    public SUSchemaObject(
            SUExecutableSchema executableSchema,
            SUObjectType type) {
        super(executableSchema, type);
    }

    @Override
    public List<SUSchemaInterface> getInterfaces() {
        List<SUInterfaceType> interfaces = getExecutableSchema()
                .getSchema()
                .getInterfaces(getObjectTypeVertex());
        List<SUSchemaInterface> result =
                new ArrayList<>(interfaces.size());
        for (SUInterfaceType interfaceType : interfaces) {
            result.add(new SUSchemaInterface(
                    getExecutableSchema(),
                    interfaceType));
        }
        return Collections.unmodifiableList(result);
    }

    @Internal
    public SUObjectType getObjectTypeVertex() {
        return (SUObjectType) getTypeVertex();
    }
}
