package graphql.introspection

class TestQueries {
    public static def NON_INTROSPECTION_QUERY_NAME = 'NonIntrospectionQuery'
    public static def INTROSPECTION_QUERY_WITH_ALIAS_NAME = 'IntrospectionQueryWithAlias'
    public static def INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME = 'IntrospectionQueryWithAliasAndMixedFields'
    public static def INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME = 'IntrospectionQueryWithSchemaFragment'
    public static def HERO_QUERY_NAME = 'HeroNameAndFriendsQuery'

    static def INTROSPECTION_QUERY_FRAGMENT_SCHEMA = """
        fragment Schema on __Schema {
          ${IntrospectionQuery.INTROSPECTION_QUERY_SCHEMA_SNIPPET}
        }
    """

    static def HERO_QUERY_VARDEF = '''$varDef'''

    static def HERO_QUERY_SNIPPET = """
          hero(id : ${HERO_QUERY_VARDEF}) {
            id
            ...DroidFields
          }
          han: hero(id: "1001") { name }
          luke: hero(ids: ["1001"]) { name }
          villain(id: "1") { name }
    """

    static def HERO_QUERY_FRAGMENT_DROIDFIELDS = '''
        fragment DroidFields on Droid {
          primaryFunction
        }
    '''


    public static def NON_INTROSPECTION_QUERY = """
        query ${NON_INTROSPECTION_QUERY_NAME} {
          schema {
            ${IntrospectionQuery.INTROSPECTION_QUERY_SCHEMA_SNIPPET}
          }
        }
        ${IntrospectionQuery.INTROSPECTION_QUERY_FRAGMENTS}
    """

    public static def INTROSPECTION_QUERY_WITH_ALIAS = """
        query ${INTROSPECTION_QUERY_WITH_ALIAS_NAME} {
          myAlias: __schema {
            ${IntrospectionQuery.INTROSPECTION_QUERY_SCHEMA_SNIPPET}
          }
        }
        ${IntrospectionQuery.INTROSPECTION_QUERY_FRAGMENTS}
    """

    public static def INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS = """
        query ${INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}(${HERO_QUERY_VARDEF}: VarType) {
          myAlias: __schema {
            ${IntrospectionQuery.INTROSPECTION_QUERY_SCHEMA_SNIPPET}
          }
          ${HERO_QUERY_SNIPPET}
        }
        ${IntrospectionQuery.INTROSPECTION_QUERY_FRAGMENTS}
        ${HERO_QUERY_FRAGMENT_DROIDFIELDS}
    """

    public static def INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT = """
        query ${INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME} {
          __schema {
            ...Schema
          }
        }
        ${IntrospectionQuery.INTROSPECTION_QUERY_FRAGMENTS}
        ${INTROSPECTION_QUERY_FRAGMENT_SCHEMA}
    """

    public static def HERO_QUERY = """
        query ${HERO_QUERY_NAME}(${HERO_QUERY_VARDEF}: VarType) {
          ${HERO_QUERY_SNIPPET}
        }
        ${HERO_QUERY_FRAGMENT_DROIDFIELDS}
     """
}
