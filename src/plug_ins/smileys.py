import re
import logging

log = logging.getLogger("chat")

class Plugin:
    """
    ==================
    = Smiley plugin: =
    ==================
    This plugins transforms graphical smileys into their textual representation
    """

    def __init__(self):
        self.smiley_url = "https://chat.cz/img/smile/%d.gif"
        self.name = "Smileys"

        self.smileys = {
            "8-)" : [7],
            ":-)" : [6],
            ":o)" : [932],
            ":-D" : [3],
            "LOL" : [1, 475, 228],
            ":-O" : [8],
            ";-)" : [4],
            "Ano" : [670],
            "BDSM bič" : [950],
            "Checheche" : [471, 83, 994],
            "Dalekohled" : [811],
            "Drink" : [148],
            "Dumám" : [92, 564],
            "Eh" : [489],
            "Facepalm" : [766, 393],
            "Fuck you!" : [244, 251],
            "Haha" : [240, 242, 405],
            "Hihi" : [470],
            "Hlavou o zeď" : [103, 1058],
            "Hm" : [9],
            "Houslista" : [807],
            "Hvězda" : [465],
            "Jazýček" : [248],
            "Jdoucí" : [368],
            "Klaním se" : [502],
            "Kmitající jazyk" : [990],
            "Kobra" : [82],
            "Kozy ven" : [232],
            "Kroutí hlavou" : [861],
            "Ksichtík" : [333],
            "Kuk" : [439],
            "Levitace" : [1050],
            "Mrk" : [680],
            "Mává" : [645, 646, 743, 987, 325, 472],
            "Nářadí" : [61],
            "Ne-e" : [850],
            "Nota" : [294],
            "Noviny" : [1060],
            "Objímám" : [799],
            "Orál" : [622, 974],
            "Ozbrojený" : [331],
            "Palec nahoru" : [606],
            "Pavouk" : [88],
            "Pěst z monitoru" : [804],
            "Pohlazení" : [838],
            "Poslouchá hudbu" : [682],
            "Pojď sem" : [584],
            "Pole dance" : [54, 536],
            "Překvapený" : [10],
            "Pšššt" : [596],
            "Pusa" : [993],
            "ROFL" : [202, 568],
            "Rozcvička" : [359],
            "Růže" : [211],
            "Sebevrah" : [866],
            "Sloní uši" : [334],
            "Slunce" : [1033],
            "Soulož" : [237],
            "Sova" : [308],
            "Srdce-oči" : [5],
            "Svíčka" : [641],
            "Utíká" : [86],
            "Zdravím" : [1063],
            "Zívá" : [542],
            "Znechucený" : [76, 197],
            "Zvedá obočí" : [199],
            "Čtyřlístek" : [227],
            "Údiv" : [516],
        }

    def find_repl(self, num):
        """
        Finds smiley replacement
        :param num: int
            Smiley number
        :return: string
        """
        for repl,nums in self.smileys.items():
            if num in nums:
                return repl

        log.warning("Unknown smiley: " + self.smiley_url % num)
        return str(num)

    def process(self, data):
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
                    repl = self.find_repl(num)
                    msg = msg.replace("*%d*" % num, "*%s*" % repl)

                data.result_replies.append(prefix+msg)

        return True
