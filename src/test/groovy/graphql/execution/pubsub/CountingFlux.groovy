package graphql.execution.pubsub

import reactor.core.publisher.Flux

/**
 * A flux that counts how many values it has given out
 */
class CountingFlux {
    Flux<String> flux
    Integer count = 0

    CountingFlux(List<String> values) {
        flux = Flux.generate({ -> 0 }, { Integer counter, sink ->
            if (counter >= values.size()) {
                sink.complete()
            } else {
                sink.next(values[counter])
                count++
            }
            return counter + 1;
        })
    }
}
