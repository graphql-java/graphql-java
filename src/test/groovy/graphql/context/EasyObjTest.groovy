package graphql.context

import spock.lang.Specification

class EasyObjTest extends Specification {

    def "basic creation"() {
        when:
        def ctx = EasyObj.newObject()
                .put("string", "String")
                .put("int", 1)
                .putAll([
                "float": 10f,
                "true" : true
        ])
                .build()

        then:
        ctx.get("string") == "String"
        ctx.get("int") == 1
        ctx.get("float") == 10f
        ctx.get("true") == true
        ctx.get("null") == null

        when:
        def directCtx = EasyObj.newObject(["a": "A", "b": "B"])

        then:
        directCtx.get("a") == "A"
        directCtx.get("b") == "B"
    }
}
