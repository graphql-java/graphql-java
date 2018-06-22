package graphql.language;


import java.util.List;

public class ObjectTypeExtensionDefinition extends ObjectTypeDefinition {
    public ObjectTypeExtensionDefinition() {
        super(null);
    }

    public ObjectTypeExtensionDefinition(String name) {
        super(name);
    }

    public ObjectTypeExtensionDefinition(String name, List<Type> implementz, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        super(name, implementz, directives, fieldDefinitions);
    }

    public ObjectTypeExtensionDefinition deepCopy() {
        return new ObjectTypeExtensionDefinition(getName(),
                deepCopy(getImplements()),
                deepCopy(getDirectives()),
                deepCopy(getFieldDefinitions())
        );
    }


    @Override
    public String toString() {
        return "ObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", implements=" + getImplements() +
                ", directives=" + getDirectives() +
                ", fieldDefinitions=" + getFieldDefinitions() +
                '}';
    }
}
