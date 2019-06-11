package graphql.parser

import spock.lang.Specification

class MultiSourceReaderTest extends Specification {

    MultiSourceReader multiSource

    void cleanup() {
        multiSource.close()
    }

    def "can combine files"() {
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .reader(readerOf("multisource/c.txt"), "PartC")
                .build()
        then:
        multiSource.getLineNumber() == 0
        multiSource.getOverallLineNumber() == 0
        multiSource.getSourceName() == "PartA"

        when:
        def line = readNLines(1)
        then:
        line == "A0*******X"
        multiSource.getLineNumber() == 1
        multiSource.getOverallLineNumber() == 1
        multiSource.getSourceName() == "PartA"

        when:
        line = readNLines(1)
        then:
        line == "A1*******X"
        multiSource.getLineNumber() == 2
        multiSource.getOverallLineNumber() == 2
        multiSource.getSourceName() == "PartA"

        when:
        line = readNLines(4)
        then:
        line == "A2*******XA3*******XA4*******XA5*******X"
        multiSource.getLineNumber() == 6
        multiSource.getOverallLineNumber() == 6
        multiSource.getSourceName() == "PartA"

        // jumps into file B
        when:
        line = readNLines(1)
        then:
        line == "B0*******X"
        multiSource.getLineNumber() == 1
        multiSource.getOverallLineNumber() == 7
        multiSource.getSourceName() == "PartB"

        // jumps into file C
        when:
        readNLines(6)
        line = readNLines(1)
        then:
        line == "C1*******X"
        multiSource.getLineNumber() == 2
        multiSource.getOverallLineNumber() == 14
        multiSource.getSourceName() == "PartC"

        // reads to the end and stays there
        when:
        line = readNLines(100)
        then:
        line == "C2*******XC3*******XC4*******XC5*******X"
        multiSource.getLineNumber() == 6
        multiSource.getOverallLineNumber() == 18
        multiSource.getSourceName() == "PartC"

        when:
        line = readNLines(100)
        then:
        line == ""
        multiSource.getLineNumber() == 6
        multiSource.getOverallLineNumber() == 18
        multiSource.getSourceName() == "PartC"
    }

    def "can read all the lines as one"() {

        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(readerOf("multisource/a.graphql"), "SdlA")
                .reader(readerOf("multisource/b.graphql"), "SdlB")
                .reader(readerOf("multisource/c.graphql"), "SdlC")
                .build()

        then:

        joinLines(multiSource.readLines()) == '''
type A {
    fieldC : String
}
type B {
    fieldC : String
}
type C {
    fieldC : String
}
'''

    }


    def "does not track data if told too"() {
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .trackData(false)
                .build()
        then:
        readNLines(100)
        multiSource.getData() == []
    }

    def "can track data b y default"() {
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .build()
        then:
        readNLines(100)
        joinLines(multiSource.getData()) == '''
A0*******X
A1*******X
A2*******X
A3*******X
A4*******X
A5*******X
B0*******X
B1*******X
B2*******X
B3*******X
B4*******X
B5*******X
'''
    }

    def "can handle zero elements"() {
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .build()
        def lines = multiSource.readLines()
        then:
        lines.isEmpty()
        multiSource.getSourceName() == null
        multiSource.getLineNumber() == 0
        multiSource.getOverallLineNumber() == 0
    }

    def "can handle null source name"() {
        def sr = new StringReader("Hello\nWorld")
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(sr, null)
                .build()
        def lines = multiSource.readLines()
        then:
        lines == ["Hello", "World"]
        multiSource.getSourceName() == null
        multiSource.getLineNumber() == 1
        multiSource.getOverallLineNumber() == 1
    }

    def "can work out relative lines from overall lines"() {
        when:
        multiSource = MultiSourceReader.newMultiSourceReader()
                .reader(readerOf("multisource/a.txt"), "PartA")
                .reader(readerOf("multisource/b.txt"), "PartB")
                .reader(readerOf("multisource/b.txt"), "PartC")
                .trackData(true)
                .build()

        readNLines(100)

        def sAndL = multiSource.getSourceAndLineFromOverallLine(7)

        then:
        sAndL.line == 1
        sAndL.sourceName == "PartB"

        when:
        sAndL = multiSource.getSourceAndLineFromOverallLine(3)

        then:
        sAndL.line == 3
        sAndL.sourceName == "PartA"

        when:
        sAndL = multiSource.getSourceAndLineFromOverallLine(13)

        then:
        sAndL.line == 1
        sAndL.sourceName == "PartC"

        when:
        // wont jump past the max lines
        sAndL = multiSource.getSourceAndLineFromOverallLine(100)

        then:
        sAndL.line == 18
        sAndL.sourceName == "PartC"
    }

    String readNLines(int count) {
        def line = ""
        for (int i = 0; i < count; i++) {
            def l = multiSource.readLine()
            if (l == null) {
                return line
            }
            line += l
        }
        line
    }

    String joinLines(List<String> lines) {
        "\n" + lines.join("\n") + "\n"
    }

    Reader readerOf(String s) {
        def stream = this.class.classLoader.getResourceAsStream(s)
        return new InputStreamReader(stream)
    }
}
