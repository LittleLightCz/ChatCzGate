package com.svetylkovo.chatczgate.plugins.impl

import com.svetylkovo.chatczgate.beans.RoomMessage
import com.svetylkovo.chatczgate.beans.rojo.PrivmsgCommand
import com.svetylkovo.chatczgate.irc.IrcLayer
import com.svetylkovo.chatczgate.plugins.Plugin
import com.svetylkovo.rojo.Rojo
import com.svetylkovo.rojo.matcher.RojoMatcher
import org.ini4j.Ini


class SmileysPlugin : Plugin {
    override val name = "Smileys"
    override var enabled = false

    private val smileys = mapOf(
            "8-)" to listOf(7, 141),
            ":-)" to listOf(6, 733, 591, 933),
            ":-/" to listOf(589, 140),
            ":-3" to listOf(1018),
            ":-D" to listOf(3, 684),
            ":-O" to listOf(8, 681),
            ":-S" to listOf(675),
            ":o)" to listOf(932),
            ";-)" to listOf(4, 534),
            "Ach jo" to listOf(89, 981, 358),
            "Ahojky!!!" to listOf(930),
            "Ano" to listOf(670, 952, 42, 435),
            "Baf!" to listOf(415),
            "Balónky" to listOf(610),
            "BAN!" to listOf(886),
            "Banán" to listOf(259),
            "Baseballová pálka" to listOf(1061, 961),
            "BDSM bič" to listOf(950),
            "Božské oko" to listOf(980),
            "Bradavky" to listOf(73),
            "Bubeník" to listOf(428),
            "Checheche" to listOf(471, 83, 994, 716, 561),
            "Chlastám" to listOf(323),
            "Chytá se za hlavu" to listOf(477),
            "Chřestýš" to listOf(1039),
            "Cukající oko" to listOf(458),
            "Dalekohled" to listOf(811),
            "Dlaň" to listOf(1049),
            "Drink" to listOf(148, 1017),
            "Dumám" to listOf(92, 564, 865),
            "Eh" to listOf(489),
            "Exhibicionista" to listOf(389),
            "Exhibicionistka" to listOf(416),
            "Exit" to listOf(1068),
            "Facepalm" to listOf(766, 393),
            "Facka" to listOf(855),
            "Fuck you!" to listOf(244, 251),
            "Garfield" to listOf(277),
            "Haha" to listOf(240, 242, 405, 931, 551, 324, 754),
            "Ham ham" to listOf(326),
            "Hamburger" to listOf(264),
            "Hehe" to listOf(246, 737, 432, 970, 789, 77, 732),
            "High five" to listOf(813),
            "Hihi" to listOf(470, 763, 659),
            "Hipík" to listOf(759),
            "Hladí po hlavě" to listOf(831),
            "Hlavou o zeď" to listOf(103, 1058),
            "Hledí" to listOf(100, 570, 245, 241),
            "Hm" to listOf(9),
            "Hodný kluk" to listOf(329),
            "Houslista" to listOf(807),
            "Hovno" to listOf(912, 819),
            "Hrabe ti?" to listOf(854, 966),
            "Hvězda" to listOf(465, 613),
            "Jazýček" to listOf(248, 849),
            "Jdoucí" to listOf(368),
            "Jednooký zlobr" to listOf(198),
            "Joint" to listOf(817),
            "Jůůů" to listOf(602),
            "Kačenka" to listOf(125, 74),
            "Kecy!" to listOf(935),
            "Klaním se" to listOf(502, 46),
            "Klepe na dveře" to listOf(957),
            "Klove do země" to listOf(1023),
            "Kmitající jazyk" to listOf(990),
            "Kobra" to listOf(82),
            "Koulovačka" to listOf(1069),
            "Koulí očima" to listOf(631, 860, 683),
            "Kozy ven" to listOf(232),
            "Kočka" to listOf(79),
            "Kroutí hlavou" to listOf(861, 1055),
            "Ksichtík" to listOf(333),
            "Kuk" to listOf(439),
            "Kuřátko" to listOf(47),
            "Květina" to listOf(425, 226, 549),
            "Káva" to listOf(139, 453),
            "Lesklý úsměv" to listOf(576),
            "Levitace" to listOf(1050),
            "LOL" to listOf(1, 475, 228, 914),
            "Mhouří oči" to listOf(523),
            "Mimozemšťan" to listOf(907),
            "Mlčím" to listOf(230),
            "Motýl" to listOf(94),
            "Moment" to listOf(94, 406),
            "Mrk" to listOf(680, 503),
            "Mrtvý" to listOf(665),
            "Mává" to listOf(645, 646, 743, 987, 325, 472, 403, 533, 231),
            "Mě nepolíbíš" to listOf(1066),
            "Naštvaný" to listOf(255, 104),
            "Ne!" to listOf(391, 65),
            "Ne-e" to listOf(850, 767, 975, 340, 247, 217),
            "Nespěte!" to listOf(938),
            "Netopýr" to listOf(101),
            "Nota" to listOf(294, 84),
            "Noviny" to listOf(1060),
            "Nářadí" to listOf(61),
            "Nůž v hlavě" to listOf(1070),
            "O_o" to listOf(644),
            "Objímá" to listOf(799, 67, 530, 71),
            "Ohýnek" to listOf(1062),
            "Olizuje bonbón" to listOf(377),
            "Olíznutí" to listOf(1014, 328),
            "Orál" to listOf(622, 974),
            "Ozbrojený" to listOf(331),
            "Oči v sloup" to listOf(642),
            "Pacman" to listOf(520),
            "Palec nahoru" to listOf(606, 1042, 181, 319),
            "Papa" to listOf(623, 426),
            "Pavouk" to listOf(88),
            "Pes" to listOf(903, 627),
            "Pije" to listOf(862),
            "Piju sám" to listOf(401),
            "Pivo" to listOf(261, 239, 997, 216),
            "Piš, nebo tě sní myš!" to listOf(939),
            "Plavu" to listOf(538, 421),
            "Pláče" to listOf(218, 858, 600),
            "Pochva" to listOf(758),
            "Podprsenka" to listOf(463),
            "Pohlazení" to listOf(838),
            "Pohoršený" to listOf(474),
            "Pojď sem" to listOf(584),
            "Pole dance" to listOf(54, 536),
            "Polibek" to listOf(394),
            "Poslouchá hudbu" to listOf(682, 882, 1036),
            "Prasátko" to listOf(322),
            "Prosím" to listOf(320, 574),
            "Pusa" to listOf(993, 66),
            "Pískám si" to listOf(741),
            "Pěst z monitoru" to listOf(804),
            "Pěstní salva" to listOf(351),
            "Překvapený" to listOf(10),
            "Přesýpací hodiny" to listOf(258),
            "Příbor" to listOf(1072),
            "Pšššt" to listOf(596),
            "ROFL" to listOf(202, 568, 493, 194),
            "Rozcvička" to listOf(359),
            "Růže" to listOf(211),
            "S příborem" to listOf(254),
            "Sebevrah" to listOf(866),
            "Skákající" to listOf(208, 204),
            "Sloní uši" to listOf(334),
            "Slunce" to listOf(1033, 823),
            "Smutný" to listOf(983, 143),
            "Sněhulák" to listOf(466),
            "Soulož zezadu" to listOf(1012),
            "Soulož" to listOf(237, 236),
            "Sova" to listOf(308, 633),
            "Spermie" to listOf(824),
            "Sprcha" to listOf(398, 899),
            "Spím" to listOf(810, 539, 410, 177),
            "Srdce-oči" to listOf(5),
            "Srdíčko" to listOf(532),
            "Stopa" to listOf(909),
            "Stydlivý" to listOf(971, 707),
            "Svíčka" to listOf(641),
            "Tleská" to listOf(1044, 68),
            "Tluče kladivem" to listOf(413),
            "Tučňák se švihadlem" to listOf(409),
            "Tučňák" to listOf(411),
            "Twerk" to listOf(462),
            "Ty ty ty!" to listOf(402),
            "Třese kozama!" to listOf(235),
            "Usíná" to listOf(679),
            "Utíká" to listOf(86, 370),
            "Victory" to listOf(464),
            "Vodí kačera" to listOf(431),
            "Vyděšený" to listOf(1071),
            "Vyplazuje jazyk" to listOf(512, 996, 346, 64),
            "Vyvaluje oči" to listOf(219, 234, 209),
            "Zadek" to listOf(955),
            "Zasněný" to listOf(999),
            "Zatlouká kladivem" to listOf(548),
            "Zdravím" to listOf(1063),
            "Zkumavka" to listOf(223),
            "Znechucený" to listOf(76, 197, 678, 677),
            "Zpět!" to listOf(407),
            "Zvedá obočí" to listOf(199, 414, 392),
            "Zvrací" to listOf(200),
            "Zívá" to listOf(542, 69),
            "Údiv" to listOf(516, 355),
            "Úsměv" to listOf(2, 479),
            "Čert" to listOf(738),
            "Česká vlajka" to listOf(469),
            "Čistí zuby" to listOf(896),
            "Člověk" to listOf(135, 153),
            "Čtyřlístek" to listOf(227),
            "Ďábel" to listOf(63),
            "Šibenice" to listOf(965),
            "Šťourá se v nose" to listOf(344),
            "Žehlí" to listOf(959),
            "Žirafa" to listOf(457),
            "Žárovka" to listOf(163)
        )

    private val smileyMatcher: RojoMatcher = Rojo.matcher("\\*(\\d+)\\*")

    override fun init(config: Ini) {
        enabled = config.get("Smileys", "enabled")?.toBoolean() ?: false
    }

    override fun processPrivmsg(privmsg: PrivmsgCommand, irc: IrcLayer) {}

    override fun processRoomMessage(message: RoomMessage) {
        if (message.s == null) {
            message.t = smileyMatcher.replaceGroup(message.t, this::replaceSmiley)
        }
    }

    private fun replaceSmiley(smiley: String): String {
        val smileyNum = smiley.toInt()

        for ((smileyText, values) in smileys) {
            if (values.contains(smileyNum)) {
                return "*$smileyText*"
            }
        }

        return "*$smiley*"
    }

}