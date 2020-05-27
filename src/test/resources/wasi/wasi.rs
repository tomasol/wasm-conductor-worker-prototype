use std::env;
use serde_json::{Result, Value};

fn main() -> Result<()> {
    let args: Vec<String> = env::args().collect();
    let v: Value = serde_json::from_str(&args[1])?;
    println!("{:?}", v["name"]);
    Ok(())
}
