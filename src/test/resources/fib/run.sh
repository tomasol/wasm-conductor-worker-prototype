set -xe
wasmtime run --invoke fib ./fib.wasm 7
