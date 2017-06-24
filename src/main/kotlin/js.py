import demjson

from logger import log


def to_py_json(js_str):
    """
    Converts javascript JSON string notation to Python JSON object
    """
    try:
        str = js_str.replace(":False",":false").replace(":True",":true")
        return demjson.decode(str)
    except:
        log.exception("Error occured during Javascript to Python's JSON conversion!")
        log.error("Transformed input was:\n{0}\n".format(str))
        log.error("Original input was:\n{0}\n".format(js_str))

