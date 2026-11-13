package graphql

import spock.lang.Specification

import java.lang.reflect.Method

class JvmSpecificTest extends Specification {

    def "javac -parameters is in place"() {
        when:
        Method method = ExecutionInput.class.getDeclaredMethods().toList().find {it.name == "transform"}
        def parameters = method.getParameters()
        then:
        parameters.length == 1
        parameters[0].name == "builderConsumer" // without -parameters this would be "arg0"
    }
}
