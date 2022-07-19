const {CloudTasksClient} = require('@google-cloud/tasks');
const yenv = require('yenv')
const { v4: uuidv4 } = require('uuid');
const fs = require('fs')
const os = require('os');

// Parse 'gql-perf-arch-github_workflow.yml' file
const parsedDocument = yenv('.github/workflows/gql-perf-arch-github_workflow.yml')

// Instantiate a client
const client = new CloudTasksClient();

// Payload variables
const jobId = String(uuidv4());
const commitHash = parsedDocument.COMMIT_HASH;
const classes = parsedDocument.CLASSES;
const pullRequestNumber = parsedDocument.PULL_REQUEST_NUMBER;


async function createHttpTaskWithToken() {
  // Actions secrets (keys)
  const project = parsedDocument.PROJECT_ID;
  const queue = parsedDocument.QUEUE_ID;
  const location = parsedDocument.LOCATION;
  const url = parsedDocument.WORKFLOW_URL;
  const serviceAccountEmail = parsedDocument.SERVICE_ACCOUNT_EMAIL;

  // Construct payload
  const payloadStructure =  {
                                 "jobId": jobId,
                                 "commitHash": commitHash,
                                 "classes": classes,
                                 "pullRequest": pullRequestNumber,
                            }
  //Format payload
  const parsedPayload = JSON.stringify(JSON.stringify(payloadStructure));
  const payload = `{"argument": ${parsedPayload}}`;

  console.log(`Payload: ${payload}`);

  // Construct the fully qualified queue name
  const parent = client.queuePath(project, location, queue);

  // Construct task with oauth authorization
  const task = {
    httpRequest: {
      httpMethod: 'POST',
      url,
      oauthToken: {
        serviceAccountEmail,
      },
      body: Buffer.from(payload).toString('base64'),
    },
  };

  console.log(`Task: ${task}`);
  const request = {parent: parent, task: task};
  const [response] = await client.createTask(request);
  const name = response.name;
  console.log(`Created task ${name}`);
  console.log(`Your job id is: ${jobId}`);
}
createHttpTaskWithToken();