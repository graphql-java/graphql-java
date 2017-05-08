package graphql.schema

import spock.lang.Specification

import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject

class GraphQLTypeUtilTest extends Specification {


    def "test it builds its wrapped types"() {
        given:
        def heroType = newObject().name("Hero").build()
        def nonnull_heroType = nonNull(heroType)
        def list_nonnull_heroType = list(nonnull_heroType)
        def nonnull_list_nonnull_heroType = nonNull(list_nonnull_heroType)

        when:
        def heroTypeStr = GraphQLTypeUtil.getUnwrappedTypeName(heroType)
        def nonnull_heroType_str = GraphQLTypeUtil.getUnwrappedTypeName(nonnull_heroType)
        def list_nonnull_heroType_str = GraphQLTypeUtil.getUnwrappedTypeName(list_nonnull_heroType)
        def nonnull_list_nonnull_heroType_str = GraphQLTypeUtil.getUnwrappedTypeName(nonnull_list_nonnull_heroType)

        then:
        heroTypeStr == "Hero"
        nonnull_heroType_str == "Hero!"
        list_nonnull_heroType_str == "[Hero!]"
        nonnull_list_nonnull_heroType_str == "[Hero!]!"
    }
}
