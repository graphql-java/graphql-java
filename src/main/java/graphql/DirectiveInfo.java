package graphql;

import graphql.schema.GraphQLDirective;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DirectiveInfo {

    public static final Set<GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVES = new LinkedHashSet();

    public static final Map<String, GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVE_MAP = new LinkedHashMap<>();

    static{
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.IncludeDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.SkipDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.DeprecatedDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.SpecifiedByDirective);
    }

    static {
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.IncludeDirective.getName(), Directives.IncludeDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.SkipDirective.getName(), Directives.SkipDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.DeprecatedDirective.getName(), Directives.DeprecatedDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.SpecifiedByDirective.getName(), Directives.SpecifiedByDirective);
    }


    public static boolean isGraphqlSpecifiedDirective(String directiveName) {
        return GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.containsKey(directiveName);
    }
}
