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
            "LOL" : [1, 475, 228, 914],
            ":-O" : [8, 681],
            ":-/" : [589],
            ";-)" : [4],
            "O_o" : [644],
            "Ano" : [670, 952, 42],
            "BAN!" : [886],
            "Baseballová pálka" : [],
            "BDSM bič" : [950],
            "Božské oko" : [980],
            "Checheche" : [471, 83, 994, 716],
            "Dalekohled" : [811],
            "Drink" : [148],
            "Dumám" : [92, 564],
            "Eh" : [489],
            "Exit" : [1068],
            "Facepalm" : [766, 393],
            "Fuck you!" : [244, 251],
            "Garfield" : [277],
            "Haha" : [240, 242, 405, 931],
            "Hihi" : [470, 763],
            "Hlavou o zeď" : [103, 1058],
            "Hm" : [9],
            "Hodný kluk" : [329],
            "Houslista" : [807],
            "Hrabe ti?" : [854],
            "Hvězda" : [465],
            "Jazýček" : [248],
            "Jednooký zlobr" : [198],
            "Jdoucí" : [368],
            "Jůůů" : [602],
            "Klaním se" : [502],
            "Klove do země" : [1023],
            "Kmitající jazyk" : [990],
            "Kobra" : [82],
            "Kozy ven" : [232],
            "Kroutí hlavou" : [861],
            "Ksichtík" : [333],
            "Kuk" : [439],
            "Levitace" : [1050],
            "Mě nepolíbíš" : [1066],
            "Mrk" : [680],
            "Mává" : [645, 646, 743, 987, 325, 472],
            "Nářadí" : [61],
            "Ne-e" : [850, 767],
            "Nota" : [294],
            "Noviny" : [1060],
            "Objímá" : [799, 67],
            "Olíznutí" : [1014],
            "Olizuje bonbón" : [377],
            "Orál" : [622, 974],
            "Ozbrojený" : [331],
            "Palec nahoru" : [606, 1042],
            "Pavouk" : [88],
            "Pěst z monitoru" : [804],
            "Pochva" : [758],
            "Pohlazení" : [838],
            "Poslouchá hudbu" : [682, 882],
            "Pojď sem" : [584],
            "Pole dance" : [54, 536],
            "Překvapený" : [10],
            "Přesýpací hodiny" : [258],
            "Pšššt" : [596],
            "Pusa" : [993],
            "ROFL" : [202, 568],
            "Rozcvička" : [359],
            "Růže" : [211],
            "Sebevrah" : [866],
            "Sloní uši" : [334],
            "Slunce" : [1033],
            "Soulož" : [237, 236],
            "Sova" : [308],
            "Spermie" : [824],
            "Spím" : [810],
            "Srdce-oči" : [5],
            "Stydlivě kouká" : [971],
            "Svíčka" : [641],
            "Šibenice" : [965],
            "Tleská" : [1044],
            "Ty ty ty!" : [402],
            "Třese kozama!" : [235],
            "Utíká" : [86],
            "Zdravím" : [1063],
            "Zívá" : [542, 69],
            "Znechucený" : [76, 197, 678, 677],
            "Zvedá obočí" : [199, 414],
            "Žárovka" : [163],
            "Čtyřlístek" : [227],
            "Údiv" : [516],
            "Úsměv" : [2],
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

                msg = re.sub(r"\*(\d+)\*", lambda g: "*"+self.find_repl(int(g.group(1)))+"*", msg)
                data.result_replies.append(prefix+msg)

        return True
