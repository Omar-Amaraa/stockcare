# MILP CARE — Routage pharmaceutique à priorités dynamiques

Optimisation de tournées de livraison depuis un dépôt vers un réseau de pharmacies,
résolue en **Programmation Linéaire Mixte en Nombres Entiers** (PuLP / CBC).

---

## Ce qui change dans cette version

**Avant** — la priorité était figée : 6 profils thérapeutiques codés en dur dans le
script, avec une valeur unique par profil (`("Insuline / vaccins", 1, 0.95, ...)`).

**Maintenant** — chaque médicament porte **son propre coefficient de priorité**,
issu du référentiel `pharmacy_delivery_priorities.csv` :

| | Avant | Maintenant |
|---|---|---|
| Source de la priorité | 6 constantes dans le code | 6 058 médicaments référencés |
| Valeurs distinctes | 6 | 498 |
| Plage | 0.15 – 0.95 (inventée) | 0.058 – 0.662 (mesurée) |
| Chaîne du froid | attribut du profil | déduite par règles DCI / nom commercial |
| Modification | éditer le code Python | éditer un CSV ou un JSON |

La priorité entre directement dans la fonction objectif du MILP : elle pondère la
pénalité de retard SLA (`w_p * pr[i] * L[i]`). Une pharmacie qui attend de
l'insuline pèse donc plus lourd qu'une pharmacie qui attend des pastilles à la
nicotine, **et sa fenêtre SLA est resserrée en conséquence**.

---

## Lancement

```bash
py -m pip install -r requirements.txt
py run.py
```

| Option | Effet |
|---|---|
| `--simuler` | régénère `data/demandes.csv` par tirage sur le catalogue |
| `--time-limit N` | plafond solveur en secondes |
| `--no-carte` | n'écrit pas la carte HTML |
| `--config CHEMIN` | fichier de configuration alternatif |

Les résultats sont horodatés dans `outputs/` : un JSON structuré (consommable par
le logiciel principal) et une carte HTML interactive.

---

## Modèle métier

Une pharmacie envoie **autant de lignes de commande qu'elle a de médicaments
manquants**. Le MILP raisonne au niveau de l'arrêt, pas de la ligne : `demandes.py`
agrège donc chaque commande en un nœud unique.

| Grandeur MILP | Règle d'agrégation |
|---|---|
| `pr[i]` priorité | **max** des priorités des médicaments demandés — l'article le plus critique dicte l'urgence |
| `cc[i]` chaîne du froid | 1 dès qu'**une seule** ligne est thermosensible — tout le colis suit |
| `q[i]` charge | somme des quantités |
| `sigma[i]` déchargement | forfait + incrément par ligne |
| `T[i]` fenêtre SLA | interpolée depuis `pr[i]`, resserrée de 15 min si chaîne du froid |

Le mode d'agrégation est configurable (`max`, `moyenne`, `moyenne_ponderee`) dans
`config/config.json`. `max` est le défaut : la moyenne diluerait une insuline
isolée au milieu de vingt lignes de confort.

---

## Format d'entrée : `data/demandes.csv`

C'est le point de branchement avec le logiciel principal.

```csv
pharmacie_id,num_amm,quantite
PH01,5993016,3
PH01,4188017,1
PH02,5993024,2
```

- `pharmacie_id` — doit correspondre à un `id` de `data/reseau_sousse.json`
- `num_amm` — clé du référentiel `pharmacy_delivery_priorities.csv`
- `quantite` — nombre d'unités (bacs) ; les lignes ≤ 0 sont ignorées

Si le fichier est absent, `run.py` génère automatiquement un jeu simulé.
Les AMM non référencés sont ignorés avec un avertissement plutôt que de faire
échouer la tournée entière.

---

## Structure

```
milp_care_delivery/
├── run.py                     POINT D'ENTREE UNIQUE
├── requirements.txt
├── config/
│   ├── config.json            flotte, poids objectif, SLA, solveur
│   └── cold_chain_rules.json  motifs DCI/nom des thermosensibles
├── data/
│   ├── pharmacy_delivery_priorities.csv   référentiel des priorités
│   ├── reseau_sousse.json                 dépôt + pharmacies (GPS TomTom)
│   └── demandes.csv                       lignes de commande en entrée
├── src/
│   ├── catalogue.py           chargement CSV + règles chaîne du froid
│   ├── demandes.py            agrégation lignes → nœuds → Scenario
│   ├── geometrie.py           matrices distance/temps
│   ├── explication.py         évaluateur exact + génération d'alternatives
│   └── vrp_milp_pharma.py     cœur du MILP (inchangé)
├── scenarios/
│   └── scenarios.py           4 cas d'étude commentés
├── outputs/                   résultats horodatés
├── tests/                     53 tests
├── docs/                      cahier des charges
└── archive/                   scripts et sorties de la version précédente
```

---

## Comprendre les décisions : `scenarios/`

Un MILP renvoie une tournée optimale sans jamais dire **pourquoi** les autres
ont été écartées. Le module `explication.py` reconstruit cette justification :

1. il réimplémente **exactement** la fonction objectif du solveur, ce qui permet
   de chiffrer n'importe quelle tournée candidate ;
2. il génère des alternatives crédibles autour de l'optimum (échanger deux
   arrêts, changer de véhicule, inverser la boucle, supprimer un véhicule) ;
3. il décompose l'écart terme à terme.

« Pourquoi pas cette route ? » devient alors un nombre.

```bash
py scenarios/scenarios.py                 # les 4 scénarios
py scenarios/scenarios.py --scenario S1   # un seul
py scenarios/scenarios.py --rapport       # + rapport Markdown dans outputs/
```

| Code | Question tranchée |
|---|---|
| **S1** | Pourquoi la pharmacie la plus **proche** n'est-elle pas servie en premier ? |
| **S2** | Comment la **chaîne du froid** partitionne-t-elle la flotte ? |
| **S3** | Pourquoi le modèle **refuse** un second véhicule, même gratuit ? |
| **S4** | Quand le retard est inévitable, **qui** le modèle sacrifie-t-il ? |

Extrait de sortie (S1) :

```
> V0 : servir P. Chemli Foued avant P. Hafedh Frigui
  PLUS CHERE de 0.36 — elle fait attendre les pharmacies les plus
  prioritaires : +0.50 sur le terme de reactivite, alors qu'elle
  n'economise que 0.13 sur le poste 'transport'
  detail : transport=-0.13  reactivite=+0.50
```

Le détour vers l'insuline coûte 0.13 de carburant et rapporte 0.50 de
réactivité : c'est là, et nulle part ailleurs, que se joue la priorité.

**Garantie de fidélité.** Quatre tests vérifient que l'évaluateur reproduit
l'objectif du solveur au centième près sur les quatre scénarios. Trois autres
vérifient qu'aucune alternative générée ne bat l'optimum — si c'était le cas,
cela signalerait un `time_limit` trop court plutôt qu'une erreur d'explication.

**Un résultat inattendu (S3).** Sur la géométrie réelle de Sousse, aucune valeur
de `w_v` — pas même zéro — ne pousse le modèle à activer un second véhicule. Les
pharmacies forment une grappe à 4-5 km du dépôt tandis que les sauts internes
font moins d'1 km : un second véhicule paie ~10 d'aller-retour pour économiser
au mieux ~7 de retard. Sur ce réseau, la taille de flotte est pilotée par la
**capacité** et la **chaîne du froid**, pas par `w_v`.

---

## Configuration

Aucune constante métier n'est codée en dur. Tout est dans `config/config.json` :

- **`flotte`** — capacités `Q`, flags réfrigérés `R`, coûts `c_f` (TND/km)
- **`sla`** — ancrages priorité → fenêtre (0.66 → 45 min, 0.06 → 210 min)
- **`solveur`** — `w_p` (poids du retard), `gamma` (réactivité), `w_v` (coût
  d'activation véhicule), `time_limit`
- **`priorite.rescale`** — `false` par défaut : on utilise les valeurs brutes du
  CSV. `w_p` absorbe l'échelle, seul l'ordre relatif compte.

Pour ajouter un médicament thermosensible, éditer `config/cold_chain_rules.json` —
pas de code à toucher.

---

## Contrôle de faisabilité

Avant de lancer le solveur, `run.py` vérifie les causes structurelles
d'infaisabilité et les explique en clair plutôt que de renvoyer un « Infeasible »
opaque :

- charge totale > capacité de la flotte (avec le déficit chiffré)
- charge thermosensible > capacité réfrigérée
- commande indivisible plus grosse que le plus gros véhicule

---

## Tests

```bash
py -m pytest tests/ -v
```

Couvrent le chargement du catalogue, les règles de chaîne du froid, les trois
modes d'agrégation, la dérivation SLA (monotonie, plancher, non-extrapolation)
et la cohérence du scénario complet.

Deux tests servent de **garde-fous anti-régression** : ils échouent si les
priorités redeviennent figées (`test_priorites_reellement_dynamiques`,
`test_priorites_des_noeuds_ne_sont_pas_toutes_egales`).

---

## Limites connues

- **Distances approximées.** Haversine × 1.60 calibré sur 13 trajets TomTom réels
  à Sousse. L'écart-type des ratios observés est élevé (1.25 à 2.62) : l'ordre de
  grandeur des tournées est correct, mais un trajet individuel peut dévier. Pour
  l'exploitation réelle, basculer sur `build_matrix_from_tomtom()`
  (n×(n−1) appels API).
- **Règles de chaîne du froid à valider.** La liste de `cold_chain_rules.json`
  couvre les classes thermosensibles usuelles et détecte ~4.7 % du référentiel.
  Elle doit être revue par le pharmacien responsable avant mise en production.
- **Les priorités du CSV sont une entrée, pas une vérité.** Le solveur les
  applique fidèlement ; leur pertinence clinique relève de la couche amont.

---

Adam — Projet MILP CARE
