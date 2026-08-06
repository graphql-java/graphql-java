package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaInterface;
import graphql.schema.universe.SUInterfaceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public final class SUSchemaInterface
        extends AbstractSUSchemaNamedType implements SchemaInterface {

    @Internal
    public SUSchemaInterface(
            SUExecutableSchema executableSchema,
            SUInterfaceType type) {
        super(executableSchema, type);
    }

    @Override
    public List<SUSchemaInterface> getInterfaces() {
        List<SUInterfaceType> interfaces = getExecutableSchema()
                .getSchema()
                .getInterfaces(getInterfaceTypeVertex());
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
    public SUInterfaceType getInterfaceTypeVertex() {
        return (SUInterfaceType) getTypeVertex();
    }
}
