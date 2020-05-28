use serde_json::{json, Result, Value};
use std::env;
use std::collections::HashMap;


// if first argument is a json object, iterate over keys and add
// "key_length" attribute with length of the original string value.
// E.g. {"name":"John"} becomes {"name": "John", "name_length": 4}
fn main() -> Result<()> {
    eprintln!("logging to stderr example");
    let args: Vec<String> = env::args().collect();
    // get the first argument
    let parsed_value: Value = serde_json::from_str(&args[1])?;
    let input_map = parsed_value.as_object().unwrap();
    let mut output_map: HashMap<String, Value> = HashMap::new();
    // iterate over keys
    for (key, value) in input_map {
        output_map.insert(key.into(), value.clone());
        if let Some(str_val) = value.as_str() {
            let len = str_val.len();
            output_map.insert(format!("{}_length", key).to_string(), json!(len));
        }
    }

    println!("{}", serde_json::to_string(&output_map)?);
    Ok(())
}
