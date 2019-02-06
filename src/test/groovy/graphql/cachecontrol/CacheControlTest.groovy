package graphql.cachecontrol


import graphql.ExecutionResultImpl
import graphql.TestUtil
import graphql.execution.ExecutionPath
import graphql.schema.DataFetcher
import spock.lang.Specification

class CacheControlTest extends Specification {

    def "can build up hints when there is no extensions present"() {
        def cc = CacheControl.newCacheControl()
        cc.hint(ExecutionPath.parse("/hint/99"), 99)
        cc.hint(ExecutionPath.parse("/hint/66"), 66)
        cc.hint(ExecutionPath.parse("/hint/33/private"), 33, CacheControl.Scope.PRIVATE)
        cc.hint(ExecutionPath.parse("/hint/private"), CacheControl.Scope.PRIVATE)

        def er = ExecutionResultImpl.newExecutionResult().data("data").build()

        when:
        def newER = cc.addTo(er)
        then:
        newER.data == "data" // left alone
        newER.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                                [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                                [path: ["hint", "33", "private"], maxAge: 33, scope: "PRIVATE"],
                                [path: ["hint", "private"], scope: "PRIVATE"],
                        ]
                ]
        ]

    }

    def "can build up hints when extensions are present"() {
        def cc = CacheControl.newCacheControl()
        cc.hint(ExecutionPath.parse("/hint/99"), 99)
        cc.hint(ExecutionPath.parse("/hint/66"), 66)

        def startingExtensions = ["someExistingExt": "data"]

        def er = ExecutionResultImpl.newExecutionResult().data("data").extensions(startingExtensions).build()

        when:
        def newER = cc.addTo(er)
        then:
        newER.data == "data" // left alone
        newER.extensions.size() == 2
        newER.extensions["someExistingExt"] == "data"
        newER.extensions["cacheControl"] == [
                version: 1,
                hints  : [
                        [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                        [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                ]
        ]
    }

    def "integration test of cache control"() {
        def sdl = '''
            type Query {
                levelA : LevelB
            }
            
            type LevelB {
                levelB : LevelC
            }
            
            type LevelC {
                levelC : String
            }
        '''

        DataFetcher dfA = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, 100)
        }
        DataFetcher dfB = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, 999)
        }

        DataFetcher dfC = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, CacheControl.Scope.PRIVATE)
        }

        def graphQL = TestUtil.graphQL(sdl, [
                Query : [levelA: dfA,],
                LevelB: [levelB: dfB],
                LevelC: [levelC: dfC]
        ]).build()

        def cacheControl = CacheControl.newCacheControl()
        when:
        def er = graphQL.execute({ input ->
            input.context(cacheControl)
                    .query(' { levelA { levelB { levelC } } }')
        })
        er = cacheControl.addTo(er)
        then:
        er.errors.isEmpty()
        er.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["levelA"], maxAge: 100, scope: "PUBLIC"],
                                [path: ["levelA", "levelB"], maxAge: 999, scope: "PUBLIC"],
                                [path: ["levelA", "levelB", "levelC"], scope: "PRIVATE"],
                        ]
                ]
        ]
    }
}
