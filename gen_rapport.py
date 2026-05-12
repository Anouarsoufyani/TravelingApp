#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Génère le rapport final PDF du projet Traveling."""

from fpdf import FPDF

# ── Couleurs ──────────────────────────────────────────────────────────────────
NAVY    = (27,  58,  92)
TEAL    = (42, 125, 111)
TERRA   = (196, 96,  58)
SAND    = (245, 240, 232)
INK     = (26,  26,  24)
MUTED   = (107, 104,  96)
WHITE   = (255, 255, 255)
LIGHT   = (237, 234, 227)

class PDF(FPDF):

    def header(self):
        if self.page_no() == 1:
            return
        self.set_fill_color(*NAVY)
        self.rect(0, 0, 210, 12, 'F')
        self.set_font('Helvetica', 'B', 8)
        self.set_text_color(*WHITE)
        self.set_xy(10, 2)
        self.cell(0, 8, 'Rapport Final – Traveling | TravelShare + TravelPath', align='L')
        self.set_xy(0, 2)
        self.cell(200, 8, f'Page {self.page_no()}', align='R')
        self.set_text_color(*INK)
        self.ln(14)

    def footer(self):
        if self.page_no() == 1:
            return
        self.set_y(-12)
        self.set_font('Helvetica', '', 7)
        self.set_text_color(*MUTED)
        self.cell(0, 8, 'Sofiane Dzermane – Développement Android 2025-2026', align='C')

    # ── Helpers ───────────────────────────────────────────────────────────────

    def cover(self):
        self.add_page()
        # Fond dégradé simulé
        self.set_fill_color(*NAVY)
        self.rect(0, 0, 210, 297, 'F')
        self.set_fill_color(42, 90, 130)
        self.rect(0, 180, 210, 117, 'F')

        self.set_text_color(*WHITE)
        self.set_y(50)
        self.set_font('Helvetica', 'B', 9)
        self.cell(0, 8, 'RAPPORT DE PROJET – DÉVELOPPEMENT D\'APPLICATIONS MOBILES ANDROID', align='C')
        self.ln(14)

        self.set_font('Helvetica', 'B', 38)
        self.cell(0, 18, 'Traveling', align='C')
        self.ln(14)

        self.set_font('Helvetica', '', 16)
        self.cell(0, 10, 'TravelShare  &  TravelPath', align='C')
        self.ln(22)

        # Ligne décorative
        self.set_draw_color(*TEAL)
        self.set_line_width(2)
        self.line(70, self.get_y(), 140, self.get_y())
        self.ln(22)

        self.set_font('Helvetica', '', 11)
        self.cell(0, 8, 'Application mobile Android de partage de photos de voyage', align='C')
        self.ln(8)
        self.cell(0, 8, 'et de génération intelligente de parcours de visite', align='C')
        self.ln(40)

        # Bloc infos
        self.set_fill_color(255, 255, 255)
        self.set_text_color(*NAVY)
        self.set_x(40)
        self.set_font('Helvetica', 'B', 10)
        self.cell(130, 8, '', border=0)
        self.ln(2)

        infos = [
            ('Étudiant',       'Sofiane Dzermane'),
            ('Année',          '2025 – 2026'),
            ('Langage',        'Java  |  Android SDK'),
            ('Architecture',   'MVVM – Room – ViewModel – LiveData'),
            ('Dépôt Git',      'https://github.com/Soof2/Travelshare'),
        ]
        for label, val in infos:
            self.set_x(35)
            self.set_font('Helvetica', 'B', 10)
            self.set_text_color(*TEAL)
            self.cell(45, 9, label + ' :', align='R')
            self.set_font('Helvetica', '', 10)
            self.set_text_color(*WHITE)
            self.cell(120, 9, val)
            self.ln(9)

    def h1(self, txt):
        self.set_font('Helvetica', 'B', 14)
        self.set_text_color(*NAVY)
        self.set_fill_color(*NAVY)
        self.set_draw_color(*TEAL)
        self.ln(4)
        self.cell(0, 10, txt, fill=False, border='B', new_x='LMARGIN', new_y='NEXT')
        self.ln(4)
        self.set_text_color(*INK)

    def h2(self, txt):
        self.set_font('Helvetica', 'B', 11)
        self.set_text_color(*TEAL)
        self.ln(3)
        self.cell(0, 8, txt, new_x='LMARGIN', new_y='NEXT')
        self.ln(2)
        self.set_text_color(*INK)

    def h3(self, txt):
        self.set_font('Helvetica', 'B', 10)
        self.set_text_color(*NAVY)
        self.cell(0, 7, txt, new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(*INK)

    def body(self, txt):
        self.set_font('Helvetica', '', 10)
        self.set_text_color(*INK)
        self.multi_cell(0, 5.5, txt)
        self.ln(2)

    def bullet(self, items):
        self.set_font('Helvetica', '', 10)
        self.set_text_color(*INK)
        for item in items:
            self.set_x(self.l_margin + 5)
            self.cell(5, 5.5, chr(149))
            self.multi_cell(0, 5.5, item)
        self.ln(2)

    def callout(self, txt, color=TEAL):
        self.set_fill_color(*SAND)
        self.set_draw_color(*color)
        self.set_line_width(0.8)
        x = self.get_x()
        y = self.get_y()
        self.rect(x, y, 4, 18, 'F')
        self.set_fill_color(*SAND)
        self.rect(x+4, y, 171, 18, 'F')
        self.set_xy(x+8, y+2)
        self.set_font('Helvetica', '', 9.5)
        self.set_text_color(*INK)
        self.multi_cell(163, 4.8, txt)
        self.set_line_width(0.2)
        self.ln(3)

    def table(self, headers, rows, widths=None):
        if widths is None:
            w = 175 / len(headers)
            widths = [w] * len(headers)
        # Header
        self.set_fill_color(*NAVY)
        self.set_text_color(*WHITE)
        self.set_font('Helvetica', 'B', 9)
        for i, h in enumerate(headers):
            self.cell(widths[i], 8, h, border=1, fill=True)
        self.ln()
        # Rows
        self.set_font('Helvetica', '', 9)
        for ri, row in enumerate(rows):
            self.set_fill_color(*SAND) if ri % 2 == 0 else self.set_fill_color(*WHITE)
            self.set_text_color(*INK)
            max_lines = 1
            for ci, cell in enumerate(row):
                # Estimate lines needed
                est = len(str(cell)) / max(1, widths[ci] / 2.5)
                max_lines = max(max_lines, int(est) + 1)
            h = min(max_lines * 5, 20)
            for ci, cell in enumerate(row):
                x, y = self.get_x(), self.get_y()
                self.multi_cell(widths[ci], 5, str(cell), border=1, fill=True, max_line_height=5)
                self.set_xy(x + widths[ci], y)
            self.ln(h)
        self.ln(3)

    def schema_box(self, title, fields, color=NAVY):
        self.set_fill_color(*color)
        self.set_text_color(*WHITE)
        self.set_font('Helvetica', 'B', 9)
        self.cell(85, 7, '  ' + title, fill=True, border=0)
        self.ln(7)
        self.set_fill_color(*SAND)
        self.set_text_color(*INK)
        self.set_font('Helvetica', '', 8.5)
        for f in fields:
            self.cell(85, 5.5, '    ' + f, fill=True, border='B')
            self.ln(5.5)
        self.ln(4)

    def section_num(self, n, title):
        self.add_page()
        self.set_fill_color(*TEAL)
        self.rect(self.l_margin - 5, self.get_y() - 2, 180, 14, 'F')
        self.set_text_color(*WHITE)
        self.set_font('Helvetica', 'B', 14)
        self.cell(0, 12, f'  {n}.  {title}', new_x='LMARGIN', new_y='NEXT')
        self.set_text_color(*INK)
        self.ln(6)


# ═════════════════════════════════════════════════════════════════════════════
#  GÉNÉRATION DU RAPPORT
# ═════════════════════════════════════════════════════════════════════════════

pdf = PDF()
pdf.set_margins(17, 20, 17)
pdf.set_auto_page_break(auto=True, margin=18)

# ── PAGE DE GARDE ─────────────────────────────────────────────────────────────
pdf.cover()

# ── TABLE DES MATIÈRES ────────────────────────────────────────────────────────
pdf.add_page()
pdf.h1('Table des matières')
toc = [
    ('1.', 'Résumé du projet'),
    ('2.', 'Architecture générale'),
    ('3.', 'Partie 1 – TravelShare : conception et implémentation'),
    ('4.', 'Partie 2 – TravelPath : conception et implémentation'),
    ('5.', 'Partie 3 – Passerelle TravelShare ↔ TravelPath'),
    ('6.', 'Modèles de données (base de données Room)'),
    ('7.', 'Choix techniques et bibliothèques'),
    ('8.', 'APIs externes utilisées'),
    ('9.', 'Composants du cours utilisés'),
    ('10.', 'Difficultés rencontrées et solutions'),
    ('11.', 'Bilan et conclusion'),
]
pdf.set_font('Helvetica', '', 10)
pdf.set_text_color(*INK)
for num, title in toc:
    pdf.set_x(17)
    pdf.set_font('Helvetica', 'B', 10)
    pdf.set_text_color(*NAVY)
    pdf.cell(12, 8, num)
    pdf.set_font('Helvetica', '', 10)
    pdf.set_text_color(*INK)
    pdf.cell(0, 8, title, new_x='LMARGIN', new_y='NEXT')
    pdf.set_draw_color(*LIGHT)
    pdf.set_line_width(0.3)
    pdf.line(17, pdf.get_y(), 193, pdf.get_y())

# ═════════════════════════════════════════════════════════════════════════════
# 1. RÉSUMÉ
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('1', 'Résumé du projet')

pdf.body(
    "Dans le cadre du module de développement d'applications mobiles Android, j'ai réalisé "
    "l'application Traveling, une application complète dédiée au monde du voyage. Elle se compose "
    "de deux parties principales - TravelShare et TravelPath - reliées par une passerelle cohérente "
    "formant une expérience unifiée."
)

pdf.h2('TravelShare – Réseau social de photos de voyage')
pdf.body(
    "TravelShare permet à tout utilisateur, même sans compte, de découvrir et d'explorer des photos "
    "de voyage géolocalisées. Les photos peuvent être filtrées par catégorie, auteur, période, ou "
    "par proximité GPS. L'utilisateur peut interagir via des likes, des commentaires et signaler "
    "des contenus problématiques. "
    "En mode connecté, l'utilisateur peut publier ses propres photos (depuis la galerie ou l'appareil "
    "photo), y associer une note vocale enregistrée directement dans l'application, les partager "
    "dans des groupes privés ou les rendre publiques. Un système complet de notifications permet "
    "d'être averti des nouvelles publications par auteur, lieu ou tag suivi."
)

pdf.h2('TravelPath – Générateur de parcours intelligent')
pdf.body(
    "TravelPath permet à l'utilisateur de planifier un voyage en renseignant ses préférences : "
    "type d'activités souhaité (culture, restauration, loisirs, découverte, shopping), budget maximum, "
    "durée disponible, niveau d'effort accepté (personnes âgées, enfants, etc.) et sensibilités météo. "
    "L'application génère automatiquement trois options de parcours optimisés : économique, équilibré "
    "et confort. Chaque parcours affiche un itinéraire GPS réel entre les étapes sur une carte "
    "interactive, la météo du jour, les horaires d'ouverture typiques, et peut être exporté en PDF "
    "ou partagé par message."
)

pdf.h2('Passerelle')
pdf.body(
    "Les deux parties sont intégrées via une passerelle bidirectionnelle : depuis la fiche d'une "
    "photo TravelShare, l'utilisateur peut lancer TravelPath avec la ville pré-remplie. Depuis "
    "une étape TravelPath, il peut voir les photos TravelShare correspondantes. Une mini-galerie "
    "TravelShare est également affichée directement dans chaque étape du parcours."
)

pdf.callout(
    "L'application est développée en Java pour Android avec une architecture MVVM s'appuyant sur "
    "les composants Jetpack : Room, ViewModel et LiveData. Toutes les fonctionnalités utilisent "
    "exclusivement des services 100% gratuits et sans clé API (OpenStreetMap, OSRM, OpenMeteo)."
)

# ═════════════════════════════════════════════════════════════════════════════
# 2. ARCHITECTURE
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('2', 'Architecture générale')

pdf.h2('Pattern MVVM (Model-View-ViewModel)')
pdf.body(
    "L'application suit le pattern architectural MVVM recommandé par Google pour les applications "
    "Android modernes. Ce pattern assure une séparation claire entre trois couches :"
)
pdf.bullet([
    "Model (Données) : entités Room, DAOs, base de données SQLite locale.",
    "ViewModel : SharedViewModel (TravelShare) et TravelPathViewModel (TravelPath). Ces classes "
    "exposent les données via LiveData et contiennent toute la logique métier.",
    "View (UI) : Activities et Fragments qui observent les LiveData et mettent à jour l'interface "
    "sans jamais accéder directement à la base de données.",
])

pdf.h2('Schéma architectural')
# Schéma texte
pdf.set_font('Courier', '', 9)
pdf.set_fill_color(*SAND)
pdf.set_text_color(*NAVY)
schema_lines = [
    "  ┌─────────────────────────────────────────────────────────────┐",
    "  │               COUCHE VUE (UI)                              │",
    "  │  MainActivity  │  Fragments (×11)  │  Activities (×4)      │",
    "  └──────────────────────────┬──────────────────────────────────┘",
    "                             │  observe(LiveData)  /  appel VM",
    "  ┌──────────────────────────▼──────────────────────────────────┐",
    "  │               COUCHE VIEWMODEL                              │",
    "  │     SharedViewModel              TravelPathViewModel        │",
    "  └──────────────────────────┬──────────────────────────────────┘",
    "                             │  Room DAO calls",
    "  ┌──────────────────────────▼──────────────────────────────────┐",
    "  │               COUCHE DONNÉES (Room/SQLite)                  │",
    "  │  13 Entités (@Entity)  │  13 DAOs (@Dao)  │  AppDatabase   │",
    "  └─────────────────────────────────────────────────────────────┘",
]
for line in schema_lines:
    pdf.cell(0, 5, line, new_x='LMARGIN', new_y='NEXT')
pdf.set_font('Helvetica', '', 10)
pdf.set_text_color(*INK)
pdf.ln(4)

pdf.h2('Organisation des packages')
pdf.table(
    ['Package', 'Contenu'],
    [
        ['ui/', '4 Activities (Main, Accueil, Login, PhotoDetail)'],
        ['ui/theme/fragments/', '11 Fragments (Explorer, Map, Publish, Groups, GroupChat, Profile, TravelPath, PlanDetail, Notifications, NotifPrefs, PathPrefs)'],
        ['ui/theme/adapters/', 'PhotoAdapter, CommentAdapter (RecyclerView)'],
        ['viewmodels/', 'SharedViewModel, TravelPathViewModel'],
        ['data/models/', '13 entités Room (@Entity)'],
        ['data/dao/', '13 DAOs Room (@Dao)'],
        ['data/', 'AppDatabase (singleton, version 13)'],
        ['utils/', 'SessionManager (SharedPreferences), NotificationUtil'],
    ],
    widths=[55, 120]
)

pdf.h2('Navigation')
pdf.body(
    "La navigation principale est assurée par une BottomNavigationView avec 5 onglets : Explorer, "
    "Carte, Publier, Groupes, Profil. Les transitions entre fragments utilisent "
    "getSupportFragmentManager().beginTransaction().replace(...).addToBackStack(null).commit(). "
    "Le bouton retour Android dépile automatiquement les fragments via popBackStack(). "
    "La communication entre composants distants utilise des Intent explicites avec putExtra() "
    "pour transmettre les données (photo ID, ville, etc.)."
)

# ═════════════════════════════════════════════════════════════════════════════
# 3. TRAVELSHARE
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('3', 'Partie 1 – TravelShare : conception et implémentation')

pdf.h2('3.1 Structure et flux de données')
pdf.body(
    "TravelShare est construit autour du SharedViewModel qui centralise l'accès à toutes les "
    "données (photos, commentaires, groupes, notifications). Le ViewModel expose des LiveData "
    "observées par les fragments. Lorsque la base de données change via Room, la LiveData notifie "
    "automatiquement l'interface utilisateur sans code supplémentaire."
)
pdf.body(
    "Exemple de flux : l'utilisateur publie une photo dans PublishFragment → le fragment appelle "
    "viewModel.insert(photo) → Room insère en base sur un thread background (databaseWriteExecutor) "
    "→ la LiveData getPublicPhotos() est automatiquement mise à jour → ExplorerFragment se "
    "rafraîchit sans action supplémentaire."
)

pdf.h2('3.2 Mode Anonyme')
pdf.body(
    "Le mode anonyme donne accès à toutes les fonctionnalités de consultation sans création de "
    "compte. La classe SessionManager (SharedPreferences) gère l'état de connexion. "
    "Quand isLoggedIn() retourne false, les boutons de publication sont désactivés mais "
    "la navigation, les likes et les signalements restent accessibles."
)
pdf.h3('Recherche et filtrage')
pdf.body(
    "ExplorerFragment implémente plusieurs modes de filtrage via un système de LiveData switchable. "
    "Une méthode observeSource(LiveData) détache l'ancienne source et observe la nouvelle, "
    "évitant les fuites mémoire. Les filtres disponibles :"
)
pdf.bullet([
    "Texte : TextWatcher → searchPhotos(query) avec LIKE en SQL",
    "Voix : Intent implicite RecognizerIntent → résultat injecté dans le TextWatcher",
    "Catégorie : chips (Nature/Urbain/Culture/Magasin) → getPhotosByCategory()",
    "Auteur : TextWatcher → getPublicPhotosByAuthor()",
    "Période : getPhotosByDateRange(start, end)",
    "GPS : LocationManager.getLastKnownLocation() → getPhotosByLocation(lat, lng, rayon 50km)",
    "Aléatoire : bouton + SHAKE du téléphone (accéléromètre) → getRandomPhotos(10)",
])

pdf.h2('3.3 Mode Connecté – Publication')
pdf.body(
    "PublishFragment gère la publication de photos avec deux sources d'image : la galerie "
    "(ActivityResultLauncher<String> avec contrat GetContent) et l'appareil photo "
    "(ActivityResultLauncher<Uri> avec contrat TakePicture + FileProvider). "
    "La géolocalisation du lieu saisi est effectuée via l'API Nominatim (OpenStreetMap) "
    "dans un thread background, puis les coordonnées sont sauvegardées avec la photo."
)
pdf.h3('Enregistrement vocal (MediaRecorder)')
pdf.body(
    "Le bouton vocal utilise un cycle d'états (IDLE → RECORDING → RECORDED) géré par une "
    "enum VoiceState. L'enregistrement utilise MediaRecorder avec le codec AAC à 44100 Hz "
    "et 128 kbps (format MPEG-4), offrant une qualité audio correcte. La permission "
    "RECORD_AUDIO est demandée au runtime via ActivityResultLauncher. La note vocale est "
    "stockée dans le cache externe et son URI est sauvegardé avec la photo en base."
)
pdf.h3('Tags automatiques')
pdf.body(
    "Le bouton 'IA : Auto-Tags' génère des hashtags en analysant le titre, le lieu et la "
    "catégorie de la photo. L'algorithme extrait les mots de plus de 3 lettres du titre, "
    "les transforme en tags, et ajoute des tags généraux selon la catégorie sélectionnée "
    "(ex : #Paysage #Pleinair pour Nature). Cette approche heuristique simule une annotation "
    "assistée par IA sans nécessiter d'API payante."
)

pdf.h2('3.4 Groupes et Chat')
pdf.body(
    "Le système de groupes fonctionne avec un modèle de demande d'adhésion : l'utilisateur "
    "envoie une demande (status=PENDING en base), le créateur du groupe reçoit une notification "
    "in-app de type JOIN_REQUEST et peut accepter ou refuser depuis l'interface de chat. "
    "Le chat de groupe (GroupChatFragment) présente un flux unifié mélangeant messages texte "
    "et photos partagées dans le groupe, triés chronologiquement via un MediatorLiveData. "
    "Les messages non lus sont comptabilisés via une requête Room et affichés comme badge "
    "rouge sur l'avatar du groupe."
)

pdf.h2('3.5 Notifications')
pdf.body(
    "Deux niveaux de notifications sont implémentés : les notifications système Android "
    "(NotificationUtil + NotificationChannel) qui s'affichent dans la barre de statut, et "
    "les notifications in-app stockées en base (table app_notifications) avec un centre de "
    "notifications dédié (NotificationsFragment). Les types gérés sont : LIKE, COMMENT, "
    "GROUP_MESSAGE, JOIN_REQUEST, JOIN_ACCEPTED. L'utilisateur peut configurer ses préférences "
    "via NotificationPreferencesFragment pour recevoir des alertes par auteur, lieu ou tag."
)

pdf.h2('3.6 Capteur – Accéléromètre (SensorManager)')
pdf.body(
    "ExplorerFragment implémente SensorEventListener et s'enregistre auprès du SensorManager "
    "avec getDefaultSensor(TYPE_ACCELEROMETER) dans onResume(), et se désenregistre dans "
    "onPause() pour économiser la batterie. Dans onSensorChanged(), les variations d'accélération "
    "sur les axes X, Y, Z sont analysées. Si la variation dépasse 12 m/s² sur au moins 2 axes "
    "simultanément (shake détecté), un flux de photos aléatoires est déclenché avec un cooldown "
    "de 1.5 secondes pour éviter les déclenchements intempestifs."
)

# ═════════════════════════════════════════════════════════════════════════════
# 4. TRAVELPATH
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('4', 'Partie 2 – TravelPath : conception et implémentation')

pdf.h2('4.1 Saisie des préférences')
pdf.body(
    "TravelPathFragment présente un formulaire complet avec :"
)
pdf.bullet([
    "CheckBox pour les 5 types d'activités (Culture, Restauration, Loisirs, Découverte, Shopping)",
    "SeekBar pour le budget max (0-500€) et la durée (1-11h)",
    "RadioGroup pour le niveau d'effort (Facile / Modéré / Intense)",
    "EditText pour les lieux obligatoires (séparés par virgule)",
    "CheckBox pour les sensibilités météo (Froid / Chaleur / Humidité)",
])

pdf.h2('4.2 Algorithme de génération des parcours')
pdf.body(
    "La génération est effectuée dans TravelPathViewModel sur un thread background "
    "(databaseWriteExecutor). L'algorithme se déroule en plusieurs étapes :"
)
pdf.bullet([
    "Étape 1 – Géocodage de la ville via Nominatim → obtention du centre GPS (latitude, longitude).",
    "Étape 2 – Pour chacun des 3 types (economique, equilibre, confort) : buildSteps() génère "
    "les étapes selon les activités sélectionnées et la durée (créneaux Matin/Après-midi/Soir).",
    "Étape 3 – Ajout des lieux obligatoires saisis par l'utilisateur, géocodés avec viewbox "
    "restreint autour du centre ville (±0.4° ≈ 44 km, paramètre bounded=1 de Nominatim).",
    "Étape 4 – estimateBudget() calcule le coût total ; si dépassement du budget max "
    "et type ≠ économique → parcours ignoré.",
    "Étape 5 – Géocodage de chaque étape générée avec la même restriction géographique. "
    "Validation : si la distance au centre ville dépasse 80 km → coordonnées rejetées (0,0).",
    "Étape 6 – Persistance en base Room (TravelPlan + PlanStep) et callback UI.",
])

pdf.h2('4.3 Carte et itinéraire GPS (OSRM)')
pdf.body(
    "La carte des étapes (PlanDetailFragment) utilise OSMDroid. Une fois les étapes chargées "
    "depuis Room via LiveData, les marqueurs sont positionnés sur la carte. Pour tracer "
    "l'itinéraire routier entre les étapes, une requête est envoyée à l'API OSRM "
    "(router.project-osrm.org, gratuite et open-source) avec les coordonnées des étapes "
    "au format lng,lat;lng,lat;... Le paramètre geometries=geojson demande la géométrie "
    "complète du trajet. La réponse JSON est parsée pour extraire les coordonnées de la "
    "LineString, qui sont converties en GeoPoint[] et tracées avec Polyline d'OSMDroid "
    "(couleur navy, épaisseur 9dp, bouts ronds). En cas d'échec réseau, des lignes droites "
    "de secours sont tracées entre les étapes."
)

pdf.h2('4.4 Météo du jour (OpenMeteo)')
pdf.body(
    "fetchWeather() dans PlanDetailFragment effectue deux appels réseau en séquence sur "
    "un thread background : d'abord Nominatim pour géocoder la ville du parcours, puis "
    "l'API OpenMeteo (api.open-meteo.com) avec les coordonnées obtenues pour récupérer "
    "la météo actuelle (température, weathercode). Le weathercode WMO est traduit en "
    "emoji, description et conseil d'activité. La bannière météo devient visible uniquement "
    "si la requête réussit, restant invisible sinon (dégradation gracieuse)."
)

pdf.h2('4.5 Export PDF natif')
pdf.body(
    "PlanDetailFragment utilise android.graphics.pdf.PdfDocument (classe native Android, "
    "sans bibliothèque externe) pour générer un document A4 (595x842 points). Le contenu "
    "est dessiné directement sur un Canvas avec des objets Paint configurés par type "
    "(titre, description, horaires). Le fichier est sauvegardé dans "
    "Documents/TravelPath/ via ExternalFilesDir et ouvert avec un Intent ACTION_VIEW "
    "après attribution des droits via FileProvider."
)

pdf.h2('4.6 Régénération avec pré-remplissage')
pdf.body(
    "Le bouton 'Modifier et régénérer' dans PlanDetailFragment crée une nouvelle instance "
    "de TravelPathFragment via newInstanceForRegen(plan) en passant tous les paramètres "
    "du plan (activités, budget, durée, effort, lieux, tolérances météo) comme arguments "
    "Bundle. Dans onCreateView(), le formulaire est pré-rempli : les CheckBox sont cochées "
    "selon les activités, les SeekBar repositionnées, le RadioButton d'effort sélectionné. "
    "Une bannière terracotta signale à l'utilisateur que les paramètres sont restaurés."
)

# ═════════════════════════════════════════════════════════════════════════════
# 5. PASSERELLE
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('5', 'Partie 3 – Passerelle TravelShare ↔ TravelPath')

pdf.body(
    "La passerelle constitue le lien entre les deux parties de l'application. Elle a été "
    "conçue pour être naturelle et non intrusive : l'utilisateur passe d'une partie à "
    "l'autre de manière fluide depuis des points d'entrée contextuels."
)

pdf.h2('5.1 TravelShare → TravelPath')
pdf.body(
    "Dans PhotoDetailActivity, un bouton 'Planifier un voyage ici – TravelPath' envoie "
    "un Intent explicite vers MainActivity avec les extras OPEN_TRAVELPATH=true et "
    "TRAVELPATH_CITY=location (le lieu de la photo). MainActivity détecte cet extra dans "
    "onNewIntent() et ouvre TravelPathFragment.newInstance(city) avec la ville pré-remplie. "
    "Une bannière teal 'Destination importée depuis TravelShare' informe l'utilisateur "
    "de l'origine de la navigation."
)

pdf.h2('5.2 TravelPath → TravelShare (deux mécanismes)')
pdf.h3('Mécanisme 1 : Bouton Voir Photos dans une étape')
pdf.body(
    "Dans PlanDetailFragment, chaque étape affiche un bouton 'Voir photos'. Ce bouton "
    "ouvre ExplorerFragment avec l'argument ARG_SEARCH_QUERY=step.name, ce qui déclenche "
    "automatiquement une recherche de photos correspondant au nom de l'étape (ex: "
    "'Musée principal de Paris' → photos tagguées 'musée', 'Paris', etc.)."
)
pdf.h3('Mécanisme 2 : Mini-galerie intégrée dans l\'étape')
pdf.body(
    "Si des photos TravelShare correspondent à l'étape, une mini-galerie horizontale s'affiche "
    "directement dans la fiche de l'étape. La recherche est effectuée synchroniquement "
    "(searchPhotosSync) dans le thread background, en cherchant les photos dont le titre, "
    "les tags ou la catégorie correspondent au type de l'étape."
)

pdf.h2('5.3 Explorer → TravelPath')
pdf.body(
    "Un bouton dans ExplorerFragment permet d'accéder directement à TravelPath via une "
    "FragmentTransaction, facilitant la découverte de la fonctionnalité de planification "
    "depuis la section de découverte de photos."
)

pdf.h2('5.4 Cohérence visuelle')
pdf.body(
    "La palette de couleurs est partagée entre les deux parties (navy #1B3A5C, teal #2A7D6F, "
    "sable #F5F0E8), assurant une continuité visuelle qui renforce l'impression d'une "
    "application unifiée plutôt que de deux applications distinctes."
)

# ═════════════════════════════════════════════════════════════════════════════
# 6. MODÈLES DE DONNÉES
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('6', 'Modèles de données (base de données Room)')

pdf.body(
    "La base de données tp3_database (version 13) contient 13 tables gérées par Room. "
    "Chaque entité est une classe Java annotée avec @Entity. Les DAOs (@Dao) définissent "
    "les requêtes SQL via des annotations @Query, @Insert, @Update, @Delete. "
    "AppDatabase est un singleton thread-safe utilisant le pattern Double-Checked Locking."
)

pdf.h2('6.1 Entités principales')
pdf.table(
    ['Entité', 'Table', 'Champs clés'],
    [
        ['User', 'users', 'id, login, password, nom, prenom, email'],
        ['Photo', 'photos', 'id, title, location, author, likes, latitude, longitude, date, category, tags, visibility, groupId, imageUri, voiceUri'],
        ['Comment', 'comments', 'id, photoId, userId, authorName, text, date'],
        ['Group', 'groups', 'id, name, description, creatorId'],
        ['GroupMember', 'group_members', 'id, groupId, userId, userName, status (PENDING/MEMBER)'],
        ['GroupMessage', 'group_messages', 'id, groupId, userId, authorName, message, date'],
        ['Report', 'reports', 'id, photoId, userId, date'],
        ['NotificationPreference', 'notification_prefs', 'id, userId, type, value'],
        ['AppNotification', 'app_notifications', 'id, targetUserId, type, message, photoId, groupId, date, isRead'],
        ['TravelPlan', 'travel_plans', 'id, userId, city, type, activities, budgetEur, durationHours, effort, liked, saved, requiredPlaces, weatherTolerances'],
        ['PlanStep', 'plan_steps', 'id, planId, stepOrder, name, type, timeSlot, durationMin, costEur, description, lat, lng, openingHours'],
    ],
    widths=[32, 38, 105]
)

pdf.h2('6.2 Relations entre tables')
pdf.bullet([
    "photos.groupId → groups.id (photo appartenant à un groupe)",
    "comments.photoId → photos.id (commentaires d'une photo)",
    "group_members.groupId → groups.id (membres d'un groupe)",
    "group_messages.groupId → groups.id (messages d'un groupe)",
    "plan_steps.planId → travel_plans.id (étapes d'un parcours)",
    "notification_prefs.userId → users.id (préférences d'un utilisateur)",
])

# ═════════════════════════════════════════════════════════════════════════════
# 7. CHOIX TECHNIQUES
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('7', 'Choix techniques et bibliothèques')

pdf.h2('7.1 Bibliothèques')
pdf.table(
    ['Bibliothèque', 'Version', 'Justification'],
    [
        ['Room', '2.6.1', 'ORM officiel Google, évite le SQL brut, compatible LiveData'],
        ['ViewModel + LiveData', '2.7.0', 'Survie aux rotations d\'écran, réactivité UI automatique'],
        ['OSMDroid', '6.1.18', 'Cartes OpenStreetMap gratuites, sans clé API, fonctionne hors ligne'],
        ['Glide', '4.16.0', 'Chargement asynchrone d\'images avec cache mémoire et disque'],
        ['CardView', '1.0.0', 'Composant Material Design pour les cards TravelPath et chat'],
        ['AppCompat + Material', '1.6.1 / 1.11.0', 'Composants UI Material Design, compatibilité Android 7+'],
    ],
    widths=[42, 22, 111]
)

pdf.h2('7.2 Stockage local uniquement (pas de Firebase)')
pdf.body(
    "Le choix d'un stockage 100% local via Room/SQLite a été fait pour plusieurs raisons : "
    "fonctionnement garanti hors ligne, aucune dépendance à un service tiers payant, "
    "simplicité de déploiement (pas de configuration serveur), et respect de la vie privée "
    "des données utilisateur. La contrepartie est l'absence de synchronisation entre appareils."
)

pdf.h2('7.3 Gestion de la concurrence')
pdf.body(
    "Toutes les opérations base de données sont exécutées sur AppDatabase.databaseWriteExecutor "
    "(pool de 4 threads). Les résultats sont retournés sur le thread principal via "
    "requireActivity().runOnUiThread() ou automatiquement via LiveData. Les callbacks "
    "incluent systématiquement un guard isAdded() pour éviter les crashes si le fragment "
    "est détruit avant la fin de l'opération asynchrone."
)

pdf.h2('7.4 Session utilisateur (SharedPreferences)')
pdf.body(
    "SessionManager encapsule les SharedPreferences pour stocker l'état de connexion "
    "(isLoggedIn, userId, username). Ce mécanisme léger persiste entre les redémarrages "
    "de l'application et ne dépend pas de Room, évitant les problèmes lors des migrations "
    "de base de données."
)

# ═════════════════════════════════════════════════════════════════════════════
# 8. APIs EXTERNES
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('8', 'APIs externes utilisées')

pdf.callout(
    "Toutes les APIs utilisées sont gratuites, open-source et ne nécessitent aucune clé API. "
    "Les appels sont effectués via HttpURLConnection natif Android sur des threads background."
)

pdf.table(
    ['API', 'Endpoint', 'Usage', 'Limite'],
    [
        ['Nominatim\n(OpenStreetMap)', 'nominatim.openstreetmap.org', 'Géocodage des lieux (photos + étapes TravelPath). Paramètre viewbox pour restriction géographique.', '1 req/s → Thread.sleep(1100ms)'],
        ['OSRM\n(Routing)', 'router.project-osrm.org', 'Calcul d\'itinéraire routier entre étapes. Retourne GeoJSON LineString.', 'Usage raisonnable'],
        ['OpenMeteo', 'api.open-meteo.com', 'Météo du jour : température, weathercode WMO.', '10 000 req/jour'],
        ['Overpass\n(OpenStreetMap)', 'overpass-api.de', 'Horaires d\'ouverture réels des POI. Timeout 4s, fallback sur horaires typiques.', 'Timeout 4s'],
    ],
    widths=[32, 48, 70, 25]
)

# ═════════════════════════════════════════════════════════════════════════════
# 9. COMPOSANTS DU COURS
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('9', 'Composants du cours utilisés')

pdf.table(
    ['Composant enseigné', 'Utilisation concrète dans le projet'],
    [
        ['Activity + Cycle de vie', 'MainActivity, AccueilActivity, LoginActivity, PhotoDetailActivity avec onDestroy() pour libérer MediaPlayer'],
        ['Fragment + FragmentManager', '11 fragments, beginTransaction().replace().addToBackStack().commit(), popBackStack()'],
        ['Intent explicite', 'Navigation entre activités, passage de données via putExtra()/getStringExtra()'],
        ['Intent implicite', 'Google Maps (geo:), RecognizerIntent, ACTION_SEND (partage), ACTION_VIEW (PDF)'],
        ['Intent Filter', 'MAIN + LAUNCHER déclaré dans AndroidManifest.xml pour AccueilActivity'],
        ['Permissions statiques', 'INTERNET, ACCESS_FINE_LOCATION, RECORD_AUDIO, POST_NOTIFICATIONS dans manifest'],
        ['Permissions dynamiques', 'RECORD_AUDIO et ACCESS_FINE_LOCATION via ActivityResultLauncher au runtime'],
        ['Layouts XML', '31 fichiers XML dans res/layout/ (LinearLayout, ConstraintLayout, FrameLayout)'],
        ['Views + Événements', 'setOnClickListener, TextWatcher, SeekBar.OnSeekBarChangeListener, RadioGroup'],
        ['Ressources Android', 'strings.xml, colors.xml, drawables, R.id.*, R.layout.*, R.color.*'],
        ['ViewModel (Jetpack)', 'SharedViewModel exposant LiveData pour TravelShare, TravelPathViewModel pour TravelPath'],
        ['LiveData (Jetpack)', '.observe(getViewLifecycleOwner(), ...) sur toutes les listes de données'],
        ['MutableLiveData', 'MediatorLiveData pour fusionner deux sources (onglets TravelPath)'],
        ['Room @Entity', '13 entités Java annotées représentant les tables de la base de données'],
        ['Room @Dao', '13 interfaces DAO avec @Insert, @Query, @Update, @Delete'],
        ['Room @Database', 'AppDatabase singleton avec fallbackToDestructiveMigration'],
        ['SensorManager', 'Accéléromètre dans ExplorerFragment : shake → photos aléatoires'],
        ['SensorEventListener', 'onSensorChanged() + onAccuracyChanged(), register/unregister dans onResume/onPause'],
    ],
    widths=[55, 120]
)

# ═════════════════════════════════════════════════════════════════════════════
# 10. DIFFICULTÉS
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('10', 'Difficultés rencontrées et solutions')

pdf.table(
    ['Problème', 'Cause', 'Solution'],
    [
        ['Géocodage hors de la ville cible', 'Nominatim retourne le 1er résultat mondial', 'Paramètre viewbox ±0.4° + bounded=1 + rejet si distance > 80km'],
        ['Crash BoundingBox.fromGeoPoints()', 'Méthode inexistante dans OSMDroid 6.1.18', 'Calcul manuel min/max lat/lng enveloppé dans try/catch'],
        ['Crash notifyDataSetChanged() depuis thread background', 'Room callback appelé hors main thread', 'Enveloppement dans requireActivity().runOnUiThread()'],
        ['Crash getViewLifecycleOwner() après destruction fragment', 'Callback asynchrone après onDestroyView()', 'Guard if (!isAdded()) return; avant tout accès à la vue'],
        ['Perte des données lors du bump de version DB', 'fallbackToDestructiveMigration efface toutes les tables', 'Migration propre ALTER TABLE ADD COLUMN pour les nouvelles colonnes'],
        ['Bruit lors enregistrement vocal', 'Codec AMR_NB 8kHz trop basse qualité', 'Passage à AAC 44100 Hz / 128 kbps format MPEG-4'],
        ['Lignes GPS vers d\'autres pays', 'Mêmes coordonnées erronées que le géocodage hors zone', 'Correction du géocodage avec viewbox, coordonnées invalides exclues de OSRM'],
    ],
    widths=[50, 60, 65]
)

# ═════════════════════════════════════════════════════════════════════════════
# 11. BILAN
# ═════════════════════════════════════════════════════════════════════════════
pdf.section_num('11', 'Bilan et conclusion')

pdf.h2('11.1 Fonctionnalités réalisées')
pdf.table(
    ['Partie', 'Fonctionnalités requises', 'Implémentées', 'Taux de réalisation'],
    [
        ['TravelShare', '~20', '20', '100%'],
        ['TravelPath', '~12', '11', '92%'],
        ['Passerelle', '4', '4', '100%'],
    ],
    widths=[50, 45, 40, 40]
)

pdf.body(
    "La seule fonctionnalité non implémentée est la galerie vidéo par étape dans TravelPath "
    "(le sujet mentionne 'photos et vidéos'), qui nécessiterait ExoPlayer et une source "
    "de vidéos externe, sortant du cadre d'un stockage 100% local."
)

pdf.h2('11.2 Apports du projet')
pdf.body(
    "Ce projet m'a permis de mettre en pratique l'ensemble des concepts vus en cours dans "
    "un contexte applicatif réel et cohérent. J'ai particulièrement approfondi :"
)
pdf.bullet([
    "Le pattern MVVM avec ViewModel et LiveData, qui permet une séparation nette entre "
    "la logique métier et l'interface et évite les bugs liés au cycle de vie Android.",
    "Room et les DAO, qui simplifient considérablement la gestion de la base de données "
    "par rapport à SQLite brut.",
    "La gestion des threads Android (main thread vs background thread) et les précautions "
    "à prendre pour éviter les crashes lors de callbacks asynchrones.",
    "L'intégration d'APIs REST externes gratuites (Nominatim, OSRM, OpenMeteo) via "
    "HttpURLConnection natif, sans dépendance à Retrofit ou OkHttp.",
    "Les Intents explicites et implicites pour la navigation et l'interopérabilité "
    "(ouverture de Google Maps, partage de contenu, enregistrement vocal).",
    "Les capteurs Android (accéléromètre) via SensorManager et SensorEventListener.",
])

pdf.h2('11.3 Lien Git')
pdf.callout(
    "Dépôt GitHub : https://github.com/Soof2/Travelshare\n"
    "Branche principale : main\n"
    "Langage : Java | minSdk : 24 (Android 7.0) | targetSdk : 34"
)

# ── Sauvegarde ────────────────────────────────────────────────────────────────
output_path = '/Users/so5o/AndroidStudioProjects/Travelshare/rapport_traveling.pdf'
pdf.output(output_path)
print(f"PDF généré : {output_path}")
