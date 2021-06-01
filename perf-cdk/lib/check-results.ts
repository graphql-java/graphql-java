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

const run = async () => {
    await findPerformanceResults("9e1f39b13ee7063e7f2a7ab1a5c0374e7f4fb9a8")

    // const startingSha: string | undefined = process.env.sha;
    // if (!startingSha) {
    //     console.log('no starting sha found');
    //     return;
    // }
    // console.log('starting sha', startingSha);
    // const prevCommits = await getPreviousCommits(startingSha);
    // console.log('prev commits: ', prevCommits);
    // for (const prevCommit of prevCommits) {
    //     await findPerformanceResults(prevCommit)
    // }
}

async function findPerformanceResults(sha: string): Promise<any> {
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

function getPreviousCommits(sha: string): Promise<string[]> {
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