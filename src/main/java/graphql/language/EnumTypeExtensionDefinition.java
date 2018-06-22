package graphql.language;

import java.util.List;

public class EnumTypeExtensionDefinition extends EnumTypeDefinition {
    public EnumTypeExtensionDefinition(String name) {
        super(name);
    }

    public EnumTypeExtensionDefinition(String name, List<Directive> directives) {
        super(name, directives);
    }

    public EnumTypeExtensionDefinition(String name, List<EnumValueDefinition> enumValueDefinitions, List<Directive> directives) {
        super(name, enumValueDefinitions, directives);
    }

    public EnumTypeExtensionDefinition deepCopy() {
        return new EnumTypeExtensionDefinition(getName(),
                deepCopy(getEnumValueDefinitions()),
                deepCopy(getDirectives())
        );
    }

    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "name='" + getName() + '\'' +
                ", enumValueDefinitions=" + getEnumValueDefinitions() +
                ", directives=" + getDirectives() +
                '}';
    }

}
