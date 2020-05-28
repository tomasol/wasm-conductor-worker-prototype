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
### Create taskdef
```shell script
 
curl -X POST -v \
  ${CONDUCTOR_API}/metadata/taskdefs \
  -H 'Content-Type: application/json' \
  -d '
[
    {
      "name": "fb-test___wasm",
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

### Create new workflow with two raw tasks
Add `wasi_ref` raw json task to the workflow:
```json
{
  "taskReferenceName": "wasi_ref",
  "name": "wasm",
  "inputParameters": {
    "wasmFileName": "src/test/resources/wasi/wasi.wasm",
    "function": "",
    "args": {"name":"John"},
    "dryRun": "false",
    "outputIsJson": "true"
  },
  "type": "SIMPLE"
}
```

Add `fib_ref` task taking values from first:
```json
 {
  "taskReferenceName": "fib_ref",
  "name": "wasm",
  "inputParameters": {
    "wasmFileName": "src/test/resources/fib/fib.wasm",
    "function": "fib",
    "args": "${wasi_ref.output.result.name_length}"
  },
  "type": "SIMPLE"
}
```
Connect `start` -> `ref_wasi` -> `ref_fib` -> `end`
and execute the workflow.

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
