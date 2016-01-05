import json
import re
import logging

log = logging.getLogger("chat")

def to_py_json(js_str):
    """
    Converts javascript JSON string notation to Python JSON object
    """
    try:
        str = re.sub(r"\s", "", js_str)
        str = re.sub(r"(\{|,)(\w+):", r'\1"\2":', str)
        str = re.sub(r"'(.*?)'", r'"\1"', str)
        str = re.sub(r'":(\w+)', r'":"\1"', str)
        return json.loads(str, encoding="UTF-8")
    except:
        log.exception("Error occured during Javascript to Python's JSON conversion!")
        log.error("Transformed input was:\n{0}\n".format(str))
        log.error("Original input was:\n{0}\n".format(js_str))

