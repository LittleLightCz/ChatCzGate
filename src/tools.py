UNICODE_SPACE = u'\xa0'

def to_ws(text):
    """Convenience function for converting spaces into unicode whitespaces"""
    return text.replace(' ', UNICODE_SPACE)


def from_ws(text):
    """Convenience function for converting unicode whitespaces into spaces"""
    return text.replace(UNICODE_SPACE, ' ')

def rotate(l):
    """
    Rotates list by 1
    :param l: list
    :return: list
    """
    return l[1:] + l[:1]