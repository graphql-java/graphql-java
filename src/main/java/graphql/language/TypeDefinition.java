package graphql.language;


import java.util.List;

public interface TypeDefinition<T extends TypeDefinition> extends Definition<T>{
    /**
     * @return the name of the type being defined.
     */
    String getName();

    /**
     * @return the directives of this type being defined
     */
    List<Directive> getDirectives();

    /**
     * @return a deep copy of this type definition
     */
    T deepCopy();
}
