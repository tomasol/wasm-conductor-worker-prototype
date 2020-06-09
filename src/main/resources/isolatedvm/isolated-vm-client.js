const ivm = require('isolated-vm')
const isolate = new ivm.Isolate()
const context = isolate.createContextSync()

let params = process.argv.slice(2);
let scriptFunction = params[0];

async function runCode() {
    const fn = await context.eval(`(${scriptFunction})`, { reference: true })
    return fn.result.apply(undefined, params.slice(1), { arguments: { copy: true }, result: { copy: true } })
}
runCode().then(value => console.log(value))
    .catch(error => console.error(error))
