import json
import re

def to_py_json(js_str):
    """
    Converts javascript JSON string notation to Python JSON object
    """
    str = re.sub(r"(\w+):", r'"\1":', js_str)
    str = re.sub(r"'(.*?)'", r'"\1"', str)
    str = re.sub(r":(\w+)", r':"\1"', str)
    return json.loads(str, encoding="UTF-8")
