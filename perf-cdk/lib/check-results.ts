import {
    GetObjectCommand,
    GetObjectCommandInput,
    GetObjectCommandOutput,
    ListObjectsV2Command,
    ListObjectsV2CommandInput,
    ListObjectsV2CommandOutput,
    S3Client
} from "@aws-sdk/client-s3";
import {exec, ExecException} from "child_process";
import ReadableStream = NodeJS.ReadableStream;

const REGION = "us-west-2";
const BUCKET_NAME = "graphql-java-perf-tests"

const s3Client = new S3Client({region: REGION});

interface JmhResult {
    benchmark: string // the FQN of the benchmark class + method name
    mode: string // avgt or thrpt or something else from org.openjdk.jmh.annotations.Mode
    threads: number
    forks: number
    jvm: string
    jvmArgs: string[]
    jdkVersion: string
    vmName: string
    vmVersion: string
    primaryMetric: {
        score: number,
        scoreError: number
        scoreConfidence: number[]
    }
}

async function run() {

    const startingSha: string | undefined = process.env.CURRENT_SHA
    if (!startingSha) {
        console.log('no starting sha found');
        return;
    }
    console.log('starting sha', startingSha);
    const prevPerformanceResults = await searchForPrevResultsRecursively(startingSha, 0);
    if (!prevPerformanceResults) {
        console.log('no previous perf results found')
        return;
    } else {
        console.log('found previous perf result:', prevPerformanceResults);
    }
}

async function searchForPrevResultsRecursively(sha: string, depth: number): Promise<{ data: JmhResult[], sha: string } | null> {
    if (depth > 10) {
        console.log('abort: no previous perf results found');
        return null;
    }
    const parentCommits = await getParentCommits(sha);
    console.log(`prev commits: ${parentCommits} of ${sha}`);
    // breadth first search: first checking for all direct parents of the current commit
    for (const parentCommit of parentCommits) {
        const prevCommitPerfResult = await findPerformanceResults(parentCommit)
        if (prevCommitPerfResult) {
            return {data: prevCommitPerfResult, sha: parentCommit};
        }
    }
    for (const prevCommit of parentCommits) {
        const prevCommitResults = searchForPrevResultsRecursively(prevCommit, depth + 1)
        if (prevCommitResults) {
            return prevCommitResults;
        }
    }
    return null;
}

async function findPerformanceResults(sha: string): Promise<JmhResult[] | null> {
    const listInput: ListObjectsV2CommandInput = {
        Bucket: BUCKET_NAME,
        Prefix: "jmh-results/jmh-" + sha
    };
    const listResult = await s3Client.send<ListObjectsV2CommandInput, ListObjectsV2CommandOutput>(new ListObjectsV2Command(listInput));
    if (listResult.KeyCount == undefined || listResult.KeyCount > 1) {
        console.log('unexpected response: KeyCount undefined or more than one ', listResult);
        throw new Error('unexpected response: KeyCount undefined or more than one');
    }
    if (listResult.KeyCount == 0) {
        console.log(`no perf results found for ${sha}`);
        return null;
    }
    const key = listResult.Contents!![0].Key
    console.log('found jmh results:', key);
    const getInput: GetObjectCommandInput = {
        Bucket: BUCKET_NAME,
        Key: key
    }
    const getResult = await s3Client.send<GetObjectCommandInput, GetObjectCommandOutput>(new GetObjectCommand(getInput))
    const stringResult = await streamToString(getResult.Body);
    console.log('result: ', stringResult);
    return JSON.parse(stringResult);
}

function streamToString(stream: ReadableStream): Promise<string> {
    const chunks: Buffer[] = [];
    return new Promise((resolve, reject) => {
        stream.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
        stream.on('error', (err) => reject(err));
        stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    })
}

function getParentCommits(sha: string): Promise<string[]> {
    return new Promise((resolve, reject) => {
        exec("git log --pretty=%P -n 1 " + sha,
            (error: ExecException | null, stdout: string, stderr: string) => {
                if (error) {
                    console.log(`error: ${error.message}`);
                    reject(error);
                }
                if (stderr) {
                    console.log(`stderr: ${stderr}`);
                    reject(error);
                }
                console.log(`stdout: ${stdout}`);
                resolve(stdout.split(' ').map(singleString => singleString.trim()));
            });
    });
}

run();