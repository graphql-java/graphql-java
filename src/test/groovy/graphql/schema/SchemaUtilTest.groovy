package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.*

class SchemaUtilTest extends Specification {

    def "collectAllTypes"() {
        when:
        Map<String, GraphQLType> types = SchemaUtil.allTypes(starWarsSchema)
        then:
        types == [(droidType.name)         : droidType,
                  (humanType.name)         : humanType,
                  (queryType.name)         : queryType,
                  (characterInterface.name): characterInterface,
                  (episodeEnum.name)       : episodeEnum,
                  (GraphQLString.name)     : GraphQLString]

    }
}
