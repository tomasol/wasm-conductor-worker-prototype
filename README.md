# WASM conductor worker (prototype)

This worker executes wasm file with quickjs engine.

## Prerequisites
* [wasmer](https://wasmer.io/) on PATH
* java 14

## Running the example workflow

### Start the application
Set a variable to conductor API endpoint directly, bypassing the proxy:
```shell script
CONDUCTOR_API="http://localhost:8080/api"
```
Then run
```shell script
./gradlew -Dconductor.url="${CONDUCTOR_API}" run
```
This will start `QuickJsWorker` and `PythonWorker` which
in turn start polling the conductor.

### Create global taskdefs
POST to `metadata/taskdefs`:
```shell script
curl -v \
 -H 'Content-Type: application/json' \
 ${CONDUCTOR_API}/metadata/taskdefs -X POST -d '
[
    {
      "name": "GLOBAL___js",
      "type": "SIMPLE",
      "retryCount": 3,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 10,
      "timeoutSeconds": 300,
      "timeoutPolicy": "TIME_OUT_WF",
      "responseTimeoutSeconds": 180,
      "ownerEmail": "foo@bar.baz"
    },
    {
      "name": "GLOBAL___py",
      "type": "SIMPLE",
      "retryCount": 3,
      "retryLogic": "FIXED",
      "retryDelaySeconds": 10,
      "timeoutSeconds": 300,
      "timeoutPolicy": "TIME_OUT_WF",
      "responseTimeoutSeconds": 180,
      "ownerEmail": "foo@bar.baz"
    }
]
'
```

### Switch to conductor proxy
```shell script
CONDUCTOR_API="http://localhost:8088/proxy/api"
```

### Create new workflow wasm-example
POST to `/metadata/workflow` 

```shell script
curl -v \
-H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
-H 'Content-Type: application/json' \
${CONDUCTOR_API}/metadata/workflow -d '
{
    "name": "js-example",
    "description": "javascript lambdas in running in wasm",
    "ownerEmail": "foo@bar.baz",
    "version": 1,
    "schemaVersion": 2,
    "tasks": [
        {
            "taskReferenceName": "create_json_ref",
            "name": "GLOBAL___js",
            "inputParameters": {
                "args": "${workflow.input.enter_your_name}",
                "outputIsJson": "true",
                "script": "console.log(JSON.stringify({name: process.argv[1]}));"
            },
            "type": "SIMPLE",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false
        },
        {
            "taskReferenceName": "calculate_name_length_ref",
            "name": "GLOBAL___js",
            "inputParameters": {
                "args": "${create_json_ref.output.result}",
                "outputIsJson": "true",
                "script": "let json=JSON.parse(process.argv[1]); json.name_length = json.name.length; console.log(JSON.stringify(json));"
            },
            "type": "SIMPLE",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false
        }
    ]
}
'
```

### Execute the workflow
POST to `/workflow` 

```shell script
WORKFLOW_ID=$(curl -v \
  -H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
  -H 'Content-Type: application/json' \
  $CONDUCTOR_API/workflow \
  -H 'Content-Type: application/json' \
  -d '
{
  "name": "js-example",
  "version": 1,
  "input": {
    "enter_your_name": "John"
  }
}
')
```

Check result:
```shell script
curl -v \
  -H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
  "${CONDUCTOR_API}/workflow/${WORKFLOW_ID}"
```

Output of the workflow execution should contain:
```json
{
   "result": {
      "name": "John",
      "name_length": 4
   }
}
```

## Input parameters of js task
* `script` - javascript to be executed. Whatever is printed using `console.log` will be added
 to the result of the task.
* `args` - can be string, json or array of strings. This will be serialized to array of strings and passed to `wasmtime`. 
* `outputIsJson` - if set to `true`, output will be interpreted as JSON.

## Python
### Create new workflow wasm-example
POST to `/metadata/workflow` 

```shell script
curl -v \
-H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
-H 'Content-Type: application/json' \
${CONDUCTOR_API}/metadata/workflow -d @- << 'EOF'
{
    "name": "py-example",
    "description": "javascript lambdas in running in wasm",
    "ownerEmail": "foo@bar.baz",
    "version": 1,
    "schemaVersion": 2,
    "tasks": [
        {
            "taskReferenceName": "create_json_ref2",
            "name": "GLOBAL___py",
            "inputParameters": {
                "args": "${workflow.input.enter_your_name}",
                "outputIsJson": "true",
                "script": "import json;print(json.dumps({'name': argv[1]}));"
            },
            "type": "SIMPLE",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false
        }
    ]
}
EOF
```

### Python bugs, limitations:
* Syntax errors end up having status COMPLETED instead of FAILED_WITH_TERMINAL_ERROR as status code is always 0.
* Compared to QuickJs this approach introduces 5-20x worse latency for small scripts.
