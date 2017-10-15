package graphql.language;


public class TypeExtensionDefinition extends ObjectTypeDefinition {
    public TypeExtensionDefinition() {
        super(null);
    }

    public TypeExtensionDefinition(String name) {
        super(name);
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
