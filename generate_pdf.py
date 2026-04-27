#!/usr/bin/env python3
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT

OUTPUT = "/Users/so5o/AndroidStudioProjects/Travelshare/rapport_travelshare.pdf"

doc = SimpleDocTemplate(
    OUTPUT, pagesize=A4,
    leftMargin=2.5*cm, rightMargin=2.5*cm,
    topMargin=2.5*cm, bottomMargin=2.5*cm
)

styles = getSampleStyleSheet()

NAVY  = colors.HexColor("#2C3E50")
TEAL  = colors.HexColor("#1ABC9C")
LIGHT = colors.HexColor("#ECF0F1")
WHITE = colors.white
MUTED = colors.HexColor("#7F8C8D")

title_s = ParagraphStyle("T", fontSize=22, textColor=NAVY,
    alignment=TA_LEFT, fontName="Helvetica-Bold", leading=28)
sub_s   = ParagraphStyle("S", fontSize=10, textColor=MUTED,
    alignment=TA_LEFT, leading=14)
h1_s    = ParagraphStyle("H1", fontSize=13, textColor=NAVY,
    fontName="Helvetica-Bold", spaceBefore=14, spaceAfter=4, leading=16)
body_s  = ParagraphStyle("B", fontSize=9.5, textColor=NAVY,
    leading=14, spaceAfter=3, alignment=TA_JUSTIFY)
item_s  = ParagraphStyle("I", fontSize=9.5, textColor=NAVY,
    leading=14, spaceAfter=2, leftIndent=12)
foot_s  = ParagraphStyle("F", fontSize=7.5, textColor=MUTED, alignment=TA_CENTER)

def p(t):  return Paragraph(t, body_s)
def it(t): return Paragraph(f"<b>·</b>  {t}", item_s)
def sp(n=8): return Spacer(1, n)
def div(): return HRFlowable(width="100%", thickness=0.6, color=TEAL,
                              spaceBefore=2, spaceAfter=6)

def section(num, title):
    return [
        Paragraph(f"{num}.  {title}", h1_s),
        div(),
    ]

story = []

# ── TITRE ────────────────────────────────────────────────────────────────────
story += [
    Paragraph("Traveling", title_s),
    Paragraph("Rapport des parties réalisées", sub_s),
    Paragraph("Sofiane HAMMAR  &amp;  Anouar Soufyani", sub_s),
    sp(4),
    HRFlowable(width="100%", thickness=1.5, color=TEAL, spaceAfter=10),
]

# ── 1. PRÉSENTATION ──────────────────────────────────────────────────────────
story += section("1", "Présentation générale")
story += [
    p("L'application <b>Traveling</b> est une application Android combinant deux services principaux : "
      "<b>TravelShare</b> (partage de photos de voyages) "
      "et <b>TravelPath</b> (générateur de parcours de visite)."),
    sp(12),
]

# ── 2. TRAVELSHARE ───────────────────────────────────────────────────────────
story += section("2", "TravelShare – Partage et découverte de photos")
story += [
    p("La partie TravelShare couvre les fonctionnalités suivantes :"),
    sp(4),
    it("<b>Authentification :</b> écran d'accueil, inscription, connexion, mode anonyme."),
    it("<b>Exploration :</b> flux en grille, recherche textuelle et vocale, filtres par catégorie, période, auteur, rayon GPS, similarité et génération aléatoire."),
    it("<b>Fiche photo :</b> affichage de l'image, lieu, date, tags, commentaires, like/unlike, signalement, itinéraire vers le lieu (Google Maps) et photos similaires."),
    it("<b>Carte interactive :</b> vue OSMDroid avec marqueurs cliquables pointant vers la fiche photo."),
    it("<b>Publication :</b> depuis la galerie ou l'appareil photo, avec titre, lieu, catégorie, tags automatiques (IA simulée) et choix de visibilité (public / groupe / privé)."),
    it("<b>Groupes :</b> création, adhésion, chat interne, partage de photos au groupe."),
    it("<b>Notifications :</b> préférences par auteur/lieu/tag, feed in-app, notifications système Android."),
    sp(14),
]

# ── 3. TRAVELPATH ─────────────────────────────────────────────────────────────
story += section("3", "TravelPath – Générateur de parcours de visite")
story += [
    p("La partie TravelPath couvre les fonctionnalités suivantes :"),
    sp(4),
    it("<b>Préférences :</b> ville, type d'activités (culture, restauration, loisirs), budget, durée, niveau d'effort, tolérance météo et lieux obligatoires."),
    it("<b>Génération de parcours :</b> trois options calculées (Économique, Équilibré, Confort) avec étapes détaillées, créneaux horaires (matin/après-midi/soir) et métriques (budget, durée, effort)."),
    it("<b>Interactions :</b> like/unlike, sauvegarde, suppression de parcours."),
    it("<b>Mode hors-ligne :</b> toutes les données sont stockées localement via Room."),
    it("<b>Partiellement réalisé :</b> regénération avec ajustements fins, export PDF du parcours."),
    sp(14),
]

# ── 4. PASSERELLE ─────────────────────────────────────────────────────────────
story += section("4", "Passerelle – Intégration Traveling")
story += [
    p("Les deux parties sont intégrées en une application unique grâce à :"),
    sp(4),
    it("Une <b>navigation unifiée</b> (BottomNavigationView) donnant accès aux deux parties depuis un écran commun."),
    it("Un bouton <b>« Générer un parcours »</b> dans la fiche photo qui ouvre TravelPath pré-rempli avec la ville de la photo."),
    it("Un <b>SharedViewModel</b> et une <b>base Room unique</b> partagés entre TravelShare et TravelPath."),
    sp(16),
]


doc.build(story)
print(f"PDF généré : {OUTPUT}")
