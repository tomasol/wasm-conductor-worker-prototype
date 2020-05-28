# JSON modification example.

* To execute each use `run.sh`.
* To rebuild from source, use `build.sh`. 

First argument must be a json object.
Iterate over keys and add "key_length" attribute
 with length of the original string value.
 E.g. 
 ```json
 {"name":"John", "age": 30, "location": "Bratislava"}
```
becomes 
```json
{
    "name": "John",
    "name_length": 4,
    "location": "Bratislava",
    "location_length": 10,
    "age": 30
}
```
