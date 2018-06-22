package graphql.language;

import java.util.List;

public class InterfaceTypeExtensionDefinition extends InterfaceTypeDefinition {

    public InterfaceTypeExtensionDefinition(String name) {
        super(name);
    }

    public InterfaceTypeExtensionDefinition(String name, List<FieldDefinition> definitions, List<Directive> directives) {
        super(name, definitions, directives);
    }

    @Override
    public InterfaceTypeExtensionDefinition deepCopy() {
        return new InterfaceTypeExtensionDefinition(getName(),
                deepCopy(getFieldDefinitions()),
                deepCopy(getDirectives())
        );
    }

    @Override
    public String toString() {
        return "InterfaceTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", fieldDefinitions=" + getFieldDefinitions() +
                ", directives=" + getDirectives() +
                '}';
    }

}
