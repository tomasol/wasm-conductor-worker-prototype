use std::env;
use std::io::{self, Read};
use boa::{Executable, Interpreter, Lexer, Parser, Realm};
// use boa::{
//     exec::{Executor, Interpreter},
//     realm::Realm,
//     syntax::{lexer::Lexer, parser::Parser},
// };

pub fn evaluate<ARGS: std::fmt::Debug/* AsRef<str> */>(src: &str, args: &[ARGS]) {
    let full_script = format!("let process = {{argv:{:?}}};\n{}", args, src);

    eprintln!("Executing >>>\n{}\n<<<", full_script);

    let mut lexer = Lexer::new(&full_script);
    lexer.lex().unwrap();
        // .map_err(|e| JsValue::from(format!("Syntax Error: {}", e))).unwrap();

    let tokens = lexer.tokens;
    let expr = Parser::new(&tokens).parse_all().unwrap();
        //.map_err(|e| JsValue::from(format!("Parsing Error: {}", e))).unwrap();

    // Setup executor
    let realm = Realm::create();
    let mut engine = Interpreter::new(realm);

    // Setup executor
    expr.run(&mut engine).unwrap(); // returns Value
    //     .map_err(|e| JsValue::from(format!("Error: {}", e)))
    //     .map(|v| v.to_string())
}

// script source is passed as stdin, arguments will be available in `args` variable.
fn main() {
    let mut script = String::new();
    let stdin = io::stdin();
    let mut handle = stdin.lock();
    handle.read_to_string(&mut script).unwrap();

    let args: Vec<String> = env::args().collect();
    evaluate(&script, &args);
}
