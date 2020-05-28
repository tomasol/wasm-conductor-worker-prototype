set -xe
wasmtime run ./wasi.wasm '{"name":"foo"}'
