import logging
import re

import time

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

        self.unknown_smileys = set()
        self.unknown_html_file = time.strftime("%Y-%m-%d_%Hh%Mm%Ss-unknown.html")

        self.smileys = {
            "8-)" : [7, 141],
            ":-)" : [6, 733],
            ":-/" : [589, 140],
            ":-3" : [1018],
            ":-D" : [3],
            ":-O" : [8, 681],
            ":o)" : [932],
            ";-)" : [4],
            "Ach jo" : [89, 981, 358],
            "Ano" : [670, 952, 42, 435],
            "Baf!" : [415],
            "Balónky" : [610],
            "BAN!" : [886],
            "Banán" : [259],
            "Baseballová pálka" : [1061, 961],
            "BDSM bič" : [950],
            "Božské oko" : [980],
            "Bradavky" : [73],
            "Bubeník" : [428],
            "Checheche" : [471, 83, 994, 716, 561],
            "Chlastám" : [323],
            "Chytá se za hlavu" : [477],
            "Chřestýš" : [1039],
            "Dalekohled" : [811],
            "Drink" : [148],
            "Dumám" : [92, 564, 865],
            "Eh" : [489],
            "Exhibicionista" : [389],
            "Exhibicionistka" : [416],
            "Exit" : [1068],
            "Facepalm" : [766, 393],
            "Facka" : [855],
            "Fuck you!" : [244, 251],
            "Garfield" : [277],
            "Haha" : [240, 242, 405, 931, 551, 324],
            "Hamburger" : [264],
            "Hehe" : [246, 737, 432, 970, 789],
            "High five" : [813],
            "Hihi" : [470, 763, 659],
            "Hipík" : [759],
            "Hladí po hlavě" : [831],
            "Hlavou o zeď" : [103, 1058],
            "Hledí" : [100, 570, 245, 241],
            "Hm" : [9],
            "Hodný kluk" : [329],
            "Houslista" : [807],
            "Hovno" : [912],
            "Hrabe ti?" : [854, 966],
            "Hvězda" : [465, 613],
            "Jazýček" : [248, 849],
            "Jdoucí" : [368],
            "Jednooký zlobr" : [198],
            "Joint" : [817],
            "Jůůů" : [602],
            "Kačenka" : [125, 74],
            "Kecy!" : [935],
            "Klaním se" : [502, 46],
            "Klepe na dveře" : [957],
            "Klove do země" : [1023],
            "Kmitající jazyk" : [990],
            "Kobra" : [82],
            "Koulovačka" : [1069],
            "Koulí očima" : [631, 860, 683],
            "Kozy ven" : [232],
            "Kočka" : [79],
            "Kroutí hlavou" : [861, 1055],
            "Ksichtík" : [333],
            "Kuk" : [439],
            "Kuřátko" : [47],
            "Květina" : [425, 226],
            "Káva" : [139, 453],
            "Lesklý úsměv" : [576],
            "Levitace" : [1050],
            "LOL" : [1, 475, 228, 914],
            "Mhouří oči" : [523],
            "Mimozemšťan" : [907],
            "Mlčím" : [230],
            "Motýl" : [94],
            "Mrk" : [680, 503],
            "Mrtvý" : [665],
            "Mává" : [645, 646, 743, 987, 325, 472, 403, 533, 231],
            "Mě nepolíbíš" : [1066],
            "Naštvaný" : [255, 104],
            "Ne!" : [391, 65],
            "Ne-e" : [850, 767, 975, 340, 247],
            "Nespěte!" : [938],
            "Netopýr" : [101],
            "Nota" : [294, 84],
            "Noviny" : [1060],
            "Nářadí" : [61],
            "Nůž v hlavě" : [1070],
            "O_o" : [644],
            "Objímá" : [799, 67, 530, 71],
            "Ohýnek" : [1062],
            "Olizuje bonbón" : [377],
            "Olíznutí" : [1014, 328],
            "Orál" : [622, 974],
            "Ozbrojený" : [331],
            "Oči v sloup" : [642],
            "Pacman" : [520],
            "Palec nahoru" : [606, 1042, 181],
            "Papa" : [623],
            "Pavouk" : [88],
            "Pes" : [903],
            "Pije" : [862],
            "Piju sám" : [401],
            "Pivo" : [261, 239, 997],
            "Piš, nebo tě sní myš!" : [939],
            "Plavu" : [538, 421],
            "Pláče" : [218, 858, 600],
            "Pochva" : [758],
            "Podprsenka" : [463],
            "Pohlazení" : [838],
            "Pohoršený" : [474],
            "Pojď sem" : [584],
            "Pole dance" : [54, 536],
            "Polibek" : [394],
            "Poslouchá hudbu" : [682, 882, 1036],
            "Prasátko" : [322],
            "Prosím" : [320, 574],
            "Pusa" : [993, 66],
            "Pískám si" : [741],
            "Pěst z monitoru" : [804],
            "Překvapený" : [10],
            "Přesýpací hodiny" : [258],
            "Příbor" : [1072],
            "Pšššt" : [596],
            "ROFL" : [202, 568, 493],
            "Rozcvička" : [359],
            "Růže" : [211],
            "S příborem" : [254],
            "Sebevrah" : [866],
            "Skákající" : [208, 204],
            "Sloní uši" : [334],
            "Slunce" : [1033, 823],
            "Smutný" : [983, 143],
            "Sněhulák" : [466],
            "Soulož zezadu" : [1012],
            "Soulož" : [237, 236],
            "Sova" : [308],
            "Spermie" : [824],
            "Sprcha" : [398, 899],
            "Spím" : [810, 539, 410, 177],
            "Srdce-oči" : [5],
            "Stopa" : [909],
            "Stydlivý" : [971, 707],
            "Svíčka" : [641],
            "Tleská" : [1044, 68],
            "Tluče kladivem" : [413],
            "Tučňák se švihadlem" : [409],
            "Tučňák" : [411],
            "Twerk" : [462],
            "Ty ty ty!" : [402],
            "Třese kozama!" : [235],
            "Usíná" : [679],
            "Utíká" : [86, 370],
            "Victory" : [464],
            "Vyděšený" : [1071],
            "Vyplazuje jazyk" : [512, 996],
            "Vyvaluje oči" : [219, 234],
            "Zadek" : [955],
            "Zasněný" : [999],
            "Zatlouká kladivem" : [548],
            "Zdravím" : [1063],
            "Zkumavka" : [223],
            "Znechucený" : [76, 197, 678, 677],
            "Zpět!" : [407],
            "Zvedá obočí" : [199, 414, 392],
            "Zvrací" : [200],
            "Zívá" : [542, 69],
            "Údiv" : [516, 355],
            "Úsměv" : [2, 479],
            "Čert" : [738],
            "Česká vlajka" : [469],
            "Čistí zuby" : [896],
            "Člověk" : [135],
            "Čtyřlístek" : [227],
            "Šibenice" : [965],
            "Šťourá se v nose" : [344],
            "Žehlí" : [959],
            "Žirafa" : [457],
            "Žárovka" : [163],
        }

    def dump_unknown_smiley(self, num):
        self.unknown_smileys.add(num)
        extensions = ["gif", "bmp", "png", "jpg"]

        def create_element(n):
            image_tags = ('<img src="https://chat.cz/img/smile/{0}.{1}" />'.format(n, ext) for ext in extensions)
            return "{0}-{1} </br>".format(n, "".join(image_tags))

        elements = [ create_element(n) for n in self.unknown_smileys]

        html = '''
        <html>
        <body>
        {0}
        </body>
        </html>
        '''.format("".join(elements))

        with open(self.unknown_html_file, "w", encoding="UTF-8") as f:
            f.write(html)

        log.debug("Unknown smileys list dumped into HTML file: "+self.unknown_html_file)

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
        if log.getEffectiveLevel() == logging.DEBUG:
            self.dump_unknown_smiley(num)
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
