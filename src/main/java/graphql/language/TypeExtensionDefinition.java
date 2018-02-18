package graphql.language;


import java.util.List;

// This class should really be called ObjectTypeExtensionDefinition but history
public class TypeExtensionDefinition extends ObjectTypeDefinition {
    public TypeExtensionDefinition() {
        super(null);
    }

    public TypeExtensionDefinition(String name) {
        super(name);
    }

    public TypeExtensionDefinition(String name, List<Type> implementz, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        super(name, implementz, directives, fieldDefinitions);
    }

    public TypeExtensionDefinition deepCopy() {
        return new TypeExtensionDefinition(getName(),
                deepCopy(getImplements()),
                deepCopy(getDirectives()),
                deepCopy(getFieldDefinitions())
        );
    }


    @Override
    public String toString() {
        return "TypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", implements=" + getImplements() +
                ", directives=" + getDirectives() +
                ", fieldDefinitions=" + getFieldDefinitions() +
                '}';
    }
}
