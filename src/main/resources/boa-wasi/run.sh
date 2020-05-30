set -xe
echo -e "console.error('logging');\n console.log(JSON.parse(args[0]).foo);" \
 | wasmtime run ./boa-wasi.wasm '{"foo": 3}'
