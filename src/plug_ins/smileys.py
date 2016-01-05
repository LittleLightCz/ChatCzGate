import re
import logging

log = logging.getLogger("chat")

smiley_url = "https://chat.cz/img/smile/%d.gif"

name = "Smileys"

smileys = {
    "8-)" : [7],
    ":-)" : [6],
    ":-D" : [1, 475, 228],
    ":-O" : [8],
    ";-)" : [4],
    "Ano" : [670],
    "BDSM bič" : [950],
    "Checheche" : [471, 83],
    "Dumám" : [92, 564],
    "Facepalm" : [766],
    "Fuck you!" : [244, 251],
    "Haha" : [240],
    "Hihi" : [470],
    "Hlavou o zeď" : [103],
    "Hm" : [9],
    "Hvězda" : [465],
    "Jdoucí" : [368],
    "Klaním se" : [502],
    "Kmitající jazyk" : [990],
    "Kobra" : [82],
    "Kroutí hlavou" : [861],
    "Ksichtík" : [333],
    "Kuk" : [439],
    "Mrk" : [680],
    "Mává" : [645, 646, 743, 987, 325, 472],
    "Ne-e" : [850],
    "Objímám" : [799],
    "Orál" : [622, 974],
    "Pavouk" : [88],
    "Pojď sem" : [584],
    "Pole dance" : [54],
    "Překvapený" : [10],
    "Pusa" : [993],
    "ROFL" : [202],
    "Růže" : [211],
    "Sebevrah" : [866],
    "Slunce" : [1033],
    "Soulož" : [237],
    "Sova" : [308],
    "Srdce-oči" : [5],
    "Svíčka" : [641],
    "Utíká" : [86],
    "Zdravím" : [1063],
    "Zív" : [542],
    "Znechucený" : [76, 197],
    "Čtyřlístek" : [227],
    "Údiv" : [516],
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

    log.warning("Unknown smiley: " + smiley_url % num)
    return str(num)

def process(data):
    """
    Process data
    :param data: PluginData
    :return True if data should be processed by another plugin in the list, or False otherwise
    """

    # Process data ....
    if data.reply:
        m = re.match(r"(:.+?PRIVMSG.+?:)(.+)", data.reply, flags=re.DOTALL)
        if m:
            prefix, msg = m.groups()
            smiley_nums = set([int(s.group(1)) for s in re.finditer(r"\*(\d+)\*", msg)])
            for num in smiley_nums:
                repl = find_repl(num)
                msg = msg.replace("*%d*" % num, "*%s*" % repl)

            data.result_replies.append(prefix+msg)

    return True
