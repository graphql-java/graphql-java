package graphql.language;


import java.util.List;
import java.util.Map;

public interface TypeDefinition extends Node, Definition {
    /**
     * @return the name of the type being defined.
     */
    String getName();

    /**
     * @return the directives of this type being defined
     */
    List<Directive> getDirectives();

    /**
     * @return a map of all the directives by name
     */
    Map<String, Directive> getDirectivesMap();

    /**
     * returns a specific directive
     *
     * @param directiveName the directive to return
     *
     * @return the directive or null
     */
    Directive getDirective(String directiveName);

}
