package graphql.language;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EnumTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<EnumValueDefinition> enumValueDefinitions;
    private List<Directive> directives;

    public EnumTypeDefinition(String name) {
        this(name, null);
    }

    public EnumTypeDefinition(String name, List<Directive> directives) {
        this.name = name;
        this.directives = ( null == directives ) ? new ArrayList<>() : directives;
        this.enumValueDefinitions = new ArrayList<>();
    }

    public List<EnumValueDefinition> getEnumValueDefinitions() {
        return enumValueDefinitions;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(enumValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumTypeDefinition that = (EnumTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;

    }


    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "name='" + name + '\'' +
                ", enumValueDefinitions=" + enumValueDefinitions +
                ", directives=" + directives +
                '}';
    }
}
