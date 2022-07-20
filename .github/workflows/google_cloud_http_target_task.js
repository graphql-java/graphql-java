const { CloudTasksClient } = require('@google-cloud/tasks');
const yenv = require('yenv')
const { v4: uuidv4 } = require('uuid');

const parsedDocument = yenv('.github/workflows/gql-perf-arch-github_workflow.yml')
const client = new CloudTasksClient();

const constructPayload = () => {
  const jobId = String(uuidv4());
  const commitHash = parsedDocument.COMMIT_HASH;
  const classes = parsedDocument.CLASSES;
  const pullRequestNumber = parsedDocument.PULL_REQUEST_NUMBER;

  const payloadStructure = {
    "jobId": jobId,
    "commitHash": commitHash,
    "classes": classes,
    "pullRequest": pullRequestNumber,
  }
  return payloadStructure;
}

const formatPayload = (payloadStructure) => {
  const parsedPayload = JSON.stringify(JSON.stringify(payloadStructure));
  const payload = `{"argument": ${parsedPayload}}`;
  console.log(`Payload: ${payload}`);
  return payload;
}

const constructTask = (serviceAccountEmail, payload, url) => {
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
  return task;
}

const createRequestBody = (payload) => {
  const project = parsedDocument.PROJECT_ID;
  const queue = parsedDocument.QUEUE_ID;
  const location = parsedDocument.LOCATION;
  const url = parsedDocument.WORKFLOW_URL;
  const serviceAccountEmail = parsedDocument.SERVICE_ACCOUNT_EMAIL;
  const requestBody = {
      "fullyQualifiedQueueName": client.queuePath(project, location, queue),
      "task": constructTask(serviceAccountEmail, payload, url)
  }
  return requestBody;
}

async function createHttpTaskWithToken() {
  const payloadStructure = constructPayload();
  const payload = formatPayload(payloadStructure);
  const requestBody = createRequestBody(payload);
  const request = { parent: requestBody.fullyQualifiedQueueName, task: requestBody.task };
  const [response] = await client.createTask(request);
  const name = response.name;
  console.log(`Created task ${name}`);
}
createHttpTaskWithToken();
