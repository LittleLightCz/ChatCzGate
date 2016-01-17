import re

from logger import log


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
        self.enabled = True

        self.smileys = {
            "8-)" : [7, 141],
            ":-)" : [6],
            ":-/" : [589],
            ":-3" : [1018],
            ":-D" : [3],
            ":-O" : [8, 681],
            ":o)" : [932],
            ";-)" : [4],
            "Ach jo" : [89, 981],
            "Ano" : [670, 952, 42],
            "BAN!" : [886],
            "Banán" : [259],
            "Baseballová pálka" : [1061, 961],
            "BDSM bič" : [950],
            "Božské oko" : [980],
            "Bradavky" : [73],
            "Bubeník" : [428],
            "Checheche" : [471, 83, 994, 716],
            "Chlastám" : [323],
            "Dalekohled" : [811],
            "Drink" : [148],
            "Dumám" : [92, 564],
            "Eh" : [489],
            "Exhibicionista" : [389],
            "Exhibicionistka" : [416],
            "Exit" : [1068],
            "Facepalm" : [766, 393],
            "Fuck you!" : [244, 251],
            "Garfield" : [277],
            "Haha" : [240, 242, 405, 931],
            "Hehe" : [246, 737, 432],
            "Hihi" : [470, 763],
            "Hlavou o zeď" : [103, 1058],
            "Hledí" : [100, 570],
            "Hm" : [9],
            "Hodný kluk" : [329],
            "Houslista" : [807],
            "Hrabe ti?" : [854],
            "Hvězda" : [465],
            "Jazýček" : [248, 849],
            "Jdoucí" : [368],
            "Jednooký zlobr" : [198],
            "Jůůů" : [602],
            "Klaním se" : [502],
            "Klepe na dveře" : [957],
            "Klove do země" : [1023],
            "Kmitající jazyk" : [990],
            "Kobra" : [82],
            "Koulí očima" : [631, 860, 683],
            "Kozy ven" : [232],
            "Kroutí hlavou" : [861, 1055],
            "Ksichtík" : [333],
            "Kuk" : [439],
            "Levitace" : [1050],
            "LOL" : [1, 475, 228, 914],
            "Mhouří oči" : [523],
            "Mrk" : [680],
            "Mrtvý" : [665],
            "Mává" : [645, 646, 743, 987, 325, 472],
            "Mě nepolíbíš" : [1066],
            "Ne-e" : [850, 767, 975, 340],
            "Nespěte!" : [938],
            "Nota" : [294, 84],
            "Noviny" : [1060],
            "Nářadí" : [61],
            "O_o" : [644],
            "Objímá" : [799, 67],
            "Olizuje bonbón" : [377],
            "Olíznutí" : [1014],
            "Orál" : [622, 974],
            "Ozbrojený" : [331],
            "Oči v sloup" : [642],
            "Palec nahoru" : [606, 1042],
            "Papa" : [623],
            "Pavouk" : [88],
            "Piju sám" : [401],
            "Pivo" : [261],
            "Pochva" : [758],
            "Pohlazení" : [838],
            "Pohoršený" : [474],
            "Pojď sem" : [584],
            "Pole dance" : [54, 536],
            "Poslouchá hudbu" : [682, 882],
            "Prasátko" : [322],
            "Prosím" : [320],
            "Pusa" : [993],
            "Pískám si" : [741],
            "Pěst z monitoru" : [804],
            "Překvapený" : [10],
            "Přesýpací hodiny" : [258],
            "Pšššt" : [596],
            "ROFL" : [202, 568],
            "Rozcvička" : [359],
            "Růže" : [211],
            "S příborem" : [254],
            "Sebevrah" : [866],
            "Sloní uši" : [334],
            "Slunce" : [1033],
            "Smutný" : [983],
            "Sněhulák" : [466],
            "Soulož zezadu" : [1012],
            "Soulož" : [237, 236],
            "Sova" : [308],
            "Spermie" : [824],
            "Spím" : [810, 539],
            "Srdce-oči" : [5],
            "Stopa" : [909],
            "Stydlivě kouká" : [971],
            "Svíčka" : [641],
            "Tleská" : [1044],
            "Tučňák se švihadlem" : [409],
            "Tučňák" : [411],
            "Twerk" : [462],
            "Ty ty ty!" : [402],
            "Třese kozama!" : [235],
            "Usíná" : [679],
            "Utíká" : [86],
            "Victory" : [464],
            "Vyplazuje jazyk" : [512],
            "Zdravím" : [1063],
            "Zkumavka" : [223],
            "Znechucený" : [76, 197, 678, 677],
            "Zvedá obočí" : [199, 414],
            "Zívá" : [542, 69],
            "Údiv" : [516, 355],
            "Úsměv" : [2],
            "Čistí zuby" : [896],
            "Člověk" : [135],
            "Čtyřlístek" : [227],
            "Šibenice" : [965],
            "Žárovka" : [163],
            "Žirafa" : [457],
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
