package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject

class GraphQLTypeUtilTest extends Specification {

    def heroType = newObject().name("Hero").build()

    def inputType = GraphQLInputObjectType.newInputObject().name("Input").build()

    def enumType = GraphQLEnumType.newEnum().name("enumType").value("X").build()

    def "test it builds its wrapped types"() {
        given:
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

    def "isList tests"() {
        expect:
        GraphQLTypeUtil.isList(list(GraphQLString))
        !GraphQLTypeUtil.isList(nonNull(GraphQLString))
        !GraphQLTypeUtil.isList(GraphQLString)
        !GraphQLTypeUtil.isList(nonNull(list(GraphQLString)))
    }

    def "isNonNull tests"() {
        expect:
        GraphQLTypeUtil.isNonNull(nonNull(GraphQLString))
        !GraphQLTypeUtil.isNonNull(list(GraphQLString))
        !GraphQLTypeUtil.isNonNull(GraphQLString)
        !GraphQLTypeUtil.isNonNull(list(nonNull(GraphQLString)))
    }

    def "isWrapperType tests"() {
        expect:
        GraphQLTypeUtil.isWrapped(nonNull(GraphQLString))
        GraphQLTypeUtil.isWrapped(list(GraphQLString))
        !GraphQLTypeUtil.isWrapped(GraphQLString)

    }

    def "isNotWrapped tests"() {
        expect:
        !GraphQLTypeUtil.isNotWrapped(nonNull(GraphQLString))
        !GraphQLTypeUtil.isNotWrapped(list(GraphQLString))
        GraphQLTypeUtil.isNotWrapped(GraphQLString)
    }

    def "isScalar tests"() {
        expect:
        GraphQLTypeUtil.isScalar(GraphQLString)
        !GraphQLTypeUtil.isScalar(list(GraphQLString))
    }

    def "isEnum tests"() {
        expect:
        GraphQLTypeUtil.isEnum(enumType)
        !GraphQLTypeUtil.isEnum(GraphQLString)
        !GraphQLTypeUtil.isEnum(list(GraphQLString))
    }

    def "unwrap tests"() {
        when:
        def type = list(nonNull(GraphQLString))

        then:
        GraphQLTypeUtil.isList(type)


        when:
        type = GraphQLTypeUtil.unwrapOne(type)

        then:
        GraphQLTypeUtil.isNonNull(type)

        when:
        type = GraphQLTypeUtil.unwrapOne(type)

        then:
        !GraphQLTypeUtil.isWrapped(type)
        type == GraphQLString
    }

    def "unwrapAll tests"() {
        when:
        def type = list(nonNull(list(nonNull(GraphQLString))))

        then:
        GraphQLTypeUtil.getUnwrappedTypeName(type) == "[[String!]!]"


        when:
        type = GraphQLTypeUtil.unwrapAll(type)

        then:
        type == GraphQLString

        when:
        type = GraphQLTypeUtil.unwrapAll(type)

        then:
        type == GraphQLString

    }

    def "isLeaf tests"() {
        when:
        def type = GraphQLString

        then:
        GraphQLTypeUtil.isLeaf(type)

        when:
        type = enumType

        then:
        GraphQLTypeUtil.isLeaf(type)

        when:
        type = heroType

        then:
        !GraphQLTypeUtil.isLeaf(type)
    }

    def "isInput tests"() {
        when:
        def type = GraphQLString

        then:
        GraphQLTypeUtil.isInput(type)

        when:
        type = inputType

        then:
        GraphQLTypeUtil.isInput(type)

        when:
        type = enumType

        then:
        GraphQLTypeUtil.isInput(type)

        when:
        type = heroType

        then:
        !GraphQLTypeUtil.isInput(type)
    }
}
