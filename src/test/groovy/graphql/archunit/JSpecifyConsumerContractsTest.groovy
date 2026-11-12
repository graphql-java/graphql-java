package graphql.archunit

import graphql.extensions.ExtensionsBuilder
import graphql.incremental.StreamPayload
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DelegatingDataFetchingEnvironment
import org.jspecify.annotations.Nullable
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.AnnotatedParameterizedType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

class JSpecifyConsumerContractsTest extends Specification {
    @TempDir
    Path temporaryDirectory

    def "nullable API usages compile with NullAway: #fixture"() {
        when:
        def result = compileConsumer(fixture)

        then:
        assert result.exitCode == 0: result.output

        where:
        fixture << ['NullableApiUsage', 'NullableExecutionStrategyUsage', 'NullableNormalizedUsage']
    }

    def "NullAway rejects dereferencing absent arguments and traversal state"() {
        when:
        def result = compileConsumer('UnsafeApiUsage')

        then:
        result.exitCode != 0
        result.output.count('error: [NullAway]') == 5
        result.output.contains('getArgumentOrDefault')
        result.output.contains('depthFirst')
        result.output.contains('getSharedContextData')
        result.output.contains('getCurrentAccumulate')
        result.output.contains('getNewAccumulate')
    }

    def "NullAway rejects dereferencing nullable SPI results"() {
        when:
        def result = compileConsumer('UnsafeSpiUsage')

        then:
        result.exitCode != 0
        result.output.count('error: [NullAway]') == 6
    }

    // Check the published type-use annotations as well as consumer compilation.
    def "mapper accepts and returns nullable values"() {
        when:
        def method = DataFetcherFactories.getMethod('wrapDataFetcher', DataFetcher, BiFunction)
        def callback = method.annotatedParameterTypes[1] as AnnotatedParameterizedType

        then:
        !callback.annotatedActualTypeArguments[0].isAnnotationPresent(Nullable)
        callback.annotatedActualTypeArguments[1].isAnnotationPresent(Nullable)
        callback.annotatedActualTypeArguments[2].isAnnotationPresent(Nullable)
    }

    def "map contracts preserve nullable values for #owner.simpleName.#methodName"() {
        when:
        def type = owner.getMethod(methodName).annotatedReturnType as AnnotatedParameterizedType

        then:
        type.annotatedActualTypeArguments[1].isAnnotationPresent(Nullable)

        where:
        owner                             | methodName
        ExtensionsBuilder                 | 'buildExtensions'
        DelegatingDataFetchingEnvironment | 'getArguments'
        DelegatingDataFetchingEnvironment | 'getVariables'
    }

    def "stream list and its elements are independently nullable"() {
        when:
        def type = StreamPayload.getMethod('getItems').annotatedReturnType as AnnotatedParameterizedType

        then:
        type.isAnnotationPresent(Nullable)
        type.annotatedActualTypeArguments[0].isAnnotationPresent(Nullable)
    }

    private Map compileConsumer(String name) {
        def source = temporaryDirectory.resolve(name + '.java')
        getClass().getResourceAsStream('/graphql/archunit/nullability/' + name + '.java').withCloseable {
            Files.copy(it, source)
        }
        def output = temporaryDirectory.resolve(name + '.log')
        def command = [System.getProperty('nullability.javac')]
        command.addAll(compilerModuleOptions())
        command.addAll([
                '--release', '17', '-d', temporaryDirectory.toString(),
                '-classpath', System.getProperty('nullability.classpath'),
                '-processorpath', System.getProperty('nullability.processorpath'),
                '-XDcompilePolicy=simple', '--should-stop=ifError=FLOW',
                '-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true -XepOpt:NullAway:JSpecifyMode=true',
                source.toString()
        ])
        def process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        def completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
        }
        assert completed: 'Consumer compilation timed out'
        [exitCode: process.exitValue(), output: Files.readString(output)]
    }

    private static List<String> compilerModuleOptions() {
        def packages = ['api', 'file', 'main', 'model', 'parser', 'processing', 'tree', 'util', 'code', 'comp']
        packages.collect { '-J--add-exports=jdk.compiler/com.sun.tools.javac.' + it + '=ALL-UNNAMED' } + [
                '-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED',
                '-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED'
        ]
    }
}
