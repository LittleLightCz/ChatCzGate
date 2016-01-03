import re

smiley_url = "https://chat.cz/img/smile/%d.gif"

name = "Smileys"

smileys = {
    ":-D" : [1],
    ";-)" : [4],
    ":-O" : [8],
    "Srdce-oči" : [5],
    "ROFL" : [202],
    "Pojď sem" : [584],
    "Ano" : [670],
    "Mrk" : [680],
    "Svíčka" : [641],
    "Orál" : [622, 974],
    "Dumám" : [92],
    "Překvapený" : [10],
    "Hihi" : [470],
    "Checheche" : [471],
}

def find_repl(num):
    """
    Finds smiley replacement
    :param num: int
        Smiley number
    :return: string
    """
    for repl,nums in smileys.items():
        if num in nums:
            return repl

    print("Unknown smiley: " + smiley_url % num)
    return str(num)

def process(data):
    """
    Process data
    :param data: PluginData
    :return True if data should be processed by another plugin in the list, or False otherwise
    """

    # Process data ....
    if data.reply:
        m = re.match(r"(:.+?PRIVMSG.+?:)(.+)", data.reply)
        if m:
            prefix, msg = m.groups()
            smiley_nums = set([int(s.group(1)) for s in re.finditer(r"\*(\d+)\*", msg)])
            for num in smiley_nums:
                repl = find_repl(num)
                msg = msg.replace("*%d*" % num, "*%s*" % repl)

            data.result_replies.append(prefix+msg)

    return True
