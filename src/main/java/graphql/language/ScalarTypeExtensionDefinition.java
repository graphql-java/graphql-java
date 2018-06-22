package graphql.language;

import java.util.List;

public class ScalarTypeExtensionDefinition extends ScalarTypeDefinition {

    public ScalarTypeExtensionDefinition(String name) {
        super(name);
    }

    public ScalarTypeExtensionDefinition(String name, List<Directive> directives) {
        super(name, directives);
    }

    public ScalarTypeExtensionDefinition deepCopy() {
        return new ScalarTypeExtensionDefinition(getName(), deepCopy(getDirectives()));
    }

    @Override
    public String toString() {
        return "ScalarTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                '}';

    }
}
