package benchmark;

import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.parser.Parser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * See https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 * <p>
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 * <p>
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class AstPrinterBenchmark {
    /**
     * Note: this query is a redacted version of a real query
     */
    private static final Document document = Parser.parse("query fang($slip: dinner!) {\n" +
            "  instinctive(thin: $slip) {\n" +
            "    annoy {\n" +
            "      ...account\n" +
            "    }\n" +
            "    massive {\n" +
            "      ...cellar\n" +
            "    }\n" +
            "    used {\n" +
            "      ...rinse\n" +
            "    }\n" +
            "    distinct(sedate: [disarm]) {\n" +
            "      ...lamp\n" +
            "    }\n" +
            "    venomous {\n" +
            "      uninterested\n" +
            "    }\n" +
            "    correct {\n" +
            "      ...plane\n" +
            "    }\n" +
            "    drown\n" +
            "    talk {\n" +
            "      house\n" +
            "      womanly\n" +
            "      royal {\n" +
            "        ...snore\n" +
            "      }\n" +
            "      gray\n" +
            "      normal\n" +
            "      proud\n" +
            "      crate\n" +
            "      billowy {\n" +
            "        frogs\n" +
            "        abstracted\n" +
            "        market\n" +
            "        corn\n" +
            "      }\n" +
            "      tip {\n" +
            "        ...public\n" +
            "      }\n" +
            "      stick {\n" +
            "        ...precious\n" +
            "      }\n" +
            "      null {\n" +
            "        ...precious\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "fragment account on bath {\n" +
            "  purpose\n" +
            "  festive\n" +
            "  ruddy\n" +
            "}\n" +
            "\n" +
            "fragment cellar on thunder {\n" +
            "  debonair\n" +
            "  immense\n" +
            "  object\n" +
            "  moon\n" +
            "  icy {\n" +
            "    furniture\n" +
            "    historical\n" +
            "    team\n" +
            "    root\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "fragment rinse on song {\n" +
            "  reply {\n" +
            "    sticks\n" +
            "    unbecoming\n" +
            "  }\n" +
            "  love {\n" +
            "    annoying\n" +
            "    sign\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "fragment lamp on heartbreaking {\n" +
            "  innocent\n" +
            "  decorate\n" +
            "  pancake\n" +
            "  arithmetic\n" +
            "  grey\n" +
            "  brass\n" +
            "  pocket\n" +
            "}\n" +
            "\n" +
            "fragment snore on tired {\n" +
            "  share\n" +
            "  baseball\n" +
            "  suspend\n" +
            "}\n" +
            "\n" +
            "fragment settle on incompetent {\n" +
            "  name\n" +
            "  juggle\n" +
            "  depressed\n" +
            "}\n" +
            "\n" +
            "fragment few on puffy {\n" +
            "  ticket\n" +
            "  puny\n" +
            "  copy\n" +
            "  coast\n" +
            "}\n" +
            "\n" +
            "fragment plane on seat {\n" +
            "  ice\n" +
            "  mug\n" +
            "  wobble\n" +
            "  clear\n" +
            "  toys {\n" +
            "    ...few\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "fragment public on basin {\n" +
            "  different\n" +
            "  fang\n" +
            "  slip\n" +
            "  dinner\n" +
            "  instinctive\n" +
            "  thin {\n" +
            "    annoy {\n" +
            "      account\n" +
            "      massive\n" +
            "      cellar\n" +
            "      used\n" +
            "      rinse {\n" +
            "        distinct\n" +
            "      }\n" +
            "      sedate {\n" +
            "        disarm\n" +
            "      }\n" +
            "      lamp {\n" +
            "        venomous\n" +
            "      }\n" +
            "    }\n" +
            "    uninterested {\n" +
            "      ...settle\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "fragment precious on drown {\n" +
            "  talk\n" +
            "  house\n" +
            "  womanly\n" +
            "  royal {\n" +
            "    snore {\n" +
            "      gray\n" +
            "    }\n" +
            "    normal {\n" +
            "      proud\n" +
            "      crate\n" +
            "      billowy\n" +
            "      frogs\n" +
            "      abstracted {\n" +
            "        ...snore\n" +
            "      }\n" +
            "      corn {\n" +
            "        tip\n" +
            "        public\n" +
            "      }\n" +
            "      stick {\n" +
            "        ...settle\n" +
            "      }\n" +
            "      null {\n" +
            "        ...few\n" +
            "      }\n" +
            "      marry\n" +
            "      bath {\n" +
            "        purpose\n" +
            "        festive\n" +
            "      }\n" +
            "      ruddy\n" +
            "      help\n" +
            "      thunder\n" +
            "      debonair {\n" +
            "        immense\n" +
            "      }\n" +
            "      object\n" +
            "    }\n" +
            "  }\n" +
            "}");

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkAstPrinterThroughput(Blackhole blackhole) {
        printAst(blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAstPrinterAvgTime(Blackhole blackhole) {
        printAst(blackhole);
    }

    public static void printAst(Blackhole blackhole) {
        blackhole.consume(AstPrinter.printAst(document));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkAstPrinterCompactThroughput(Blackhole blackhole) {
        printAstCompact(blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAstPrinterCompactAvgTime(Blackhole blackhole) {
        printAstCompact(blackhole);
    }

    public static void printAstCompact(Blackhole blackhole) {
        blackhole.consume(AstPrinter.printAstCompact(document));
    }
}
