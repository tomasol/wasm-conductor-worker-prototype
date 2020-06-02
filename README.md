# WASM conductor worker (prototype)

This worker executes local wasm files.

## Prerequisites
* [wasmtime](https://wasmtime.dev/) on PATH
* java 14

Two example wasm projects are supplied:
* `src/test/resources/fib/` 
* `src/test/resources/wasi/`

See READMEs for more info.


## Running the example

### Start the worker server
```shell script
CONDUCTOR_API="http://localhost:8080/api" 
./gradlew -Dconductor.url="${CONDUCTOR_API}" bootRun
```

### Using proxy
## CURL=curl "http://localhost:8088/proxy/api"

### Create taskdef
POST to `metadata/taskdefs`:
Using proxy : 
```
curl \
-H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
-H 'Content-Type: application/json' \
http://localhost:8088/proxy/api/metadata/taskdefs -X POST -d ...
```

```json
[
    {
      "name": "wasm",
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
```

### Create new workflow wasm-example
POST to `/metadata/workflow` 
Using proxy : 
```
curl \
-H "x-auth-organization: fb-test" -H "x-auth-user-role: OWNER" -H "x-auth-user-email: foo" \
-H 'Content-Type: application/json' \
http://localhost:8088/proxy/api/metadata/workflow -d '
...
```

```json
{
    "name": "wasm-example",
    "description": "wasm example",
    "ownerEmail": "foo@bar.baz",
    "version": 1,
    "schemaVersion": 2,
    "tasks": [
        {
            "name": "wasm",
            "taskReferenceName": "wasi_ref",
            "inputParameters": {
                "wasmFileName": "src/test/resources/wasi/wasi.wasm",
                "args": {
                    "name": "${workflow.input.enter_your_name}"
                },
                "dryRun": "false",
                "outputIsJson": "true"
            },
            "type": "SIMPLE",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false
        },
        {
            "name": "wasm",
            "taskReferenceName": "boa_ref",
            "inputParameters": {
                "wasmFileName": "src/main/resources/boa-wasi/boa-wasi.wasm",
                "args": "${wasi_ref.output.result}",
                "outputIsJson": "true",
                "stdIn": "let json=JSON.parse(process.argv[1]); let length = json.name_length; delete json.name_length; json.name_length = length * 2; console.log(JSON.stringify(json));"
            },
            "type": "SIMPLE",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false
        }
    ]
}

```

Execute the workflow: TODO

Output of `ref_wasi` should be:
```json
{
   "result": {
      "name": "John",
      "name_length": 4
   }
}
```

Output of `ref_fib` should be:
```json
{
   "result": "3"
}
```
because `fib(4) = 3`.

Optionally replace hardcoded `ref_wasi` name parameter with following:

```json
    "args": {
      "name": "${workflow.input.enter_your_name}"
    },
```

## Input parameters of wasm task
* `function` - name of the function if wasm file was compiled using 
[interface-types](https://github.com/WebAssembly/interface-types/blob/master/proposals/interface-types/Explainer.md)
* `wasmFileName` - path to wasm file to be executed.
* `args` - can be string, json or array of strings. This will be serialized to array of strings and passed to `wasmtime`. 
* `dryRun` - if set to `true`, `wasmtime` will not be executed.
* `outputIsJson` - if set to `true`, output will be interpreted as JSON.
