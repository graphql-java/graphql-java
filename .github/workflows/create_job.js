const { CloudTasksClient } = require('@google-cloud/tasks');
const { v4: uuidv4 } = require('uuid');

const client = new CloudTasksClient();

const constructPayload = () => {
  const jobId = String(uuidv4());
  const commitHash = process.env.COMMIT_HASH;
  const branch = process.env.BRANCH;
  const classes = process.env.CLASSES;
  const pullRequestNumber = process.env.PULL_REQUEST_NUMBER;

  const payloadStructure = {
    "jobId": jobId,
    "commitHash": commitHash.trim(),
    "branch": branch.trim(),
    "classes":  convertClassesStringToArray(classes),
    "pullRequest": pullRequestNumber.trim(),
  }
  return payloadStructure;
}

const convertClassesStringToArray = (classes) => {
    const classesArray = classes.split(',');
    const trimmedClassesArray = classesArray.map(currentClass => currentClass.trim());
    return trimmedClassesArray;
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
  const project = process.env.PROJECT_ID;
  const queue = process.env.QUEUE_ID;
  const location = process.env.LOCATION;
  const url = process.env.WORKFLOW_URL
  const serviceAccountEmail = process.env.SERVICE_ACCOUNT_EMAIL;
  const requestBody = {
      "fullyQualifiedQueueName": client.queuePath(project, location, queue),
      "task": constructTask(serviceAccountEmail, payload, url)
  }
  return requestBody;
}

const constructRequest = () => {
  const payloadStructure = constructPayload();
  const payload = formatPayload(payloadStructure);
  const requestBody = createRequestBody(payload);
  const request = { parent: requestBody.fullyQualifiedQueueName, task: requestBody.task };
  return request;
}

async function createHttpTaskWithToken() {
  const request = constructRequest();
  const [response] = await client.createTask(request);
  const name = response.name;
  console.log(`Created task ${name}`);
}
createHttpTaskWithToken();
