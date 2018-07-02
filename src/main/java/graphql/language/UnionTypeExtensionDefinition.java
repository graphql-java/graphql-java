package graphql.language;

import java.util.List;

public class UnionTypeExtensionDefinition extends UnionTypeDefinition {
    public UnionTypeExtensionDefinition(String name) {
        super(name);
    }

    public UnionTypeExtensionDefinition(String name, List<Directive> directives, List<Type> memberTypes) {
        super(name, directives, memberTypes);
    }

    @Override
    public UnionTypeExtensionDefinition deepCopy() {
        return new UnionTypeExtensionDefinition(getName(),
                deepCopy(getDirectives()),
                deepCopy(getMemberTypes())
        );
    }

    @Override
    public String toString() {
        return "UnionTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                "directives=" + getDirectives() +
                ", memberTypes=" + getMemberTypes() +
                '}';
    }
}
