# StockCare — Layer 3 : moteur de routage MILP

Implémentation de la **Layer 3** du cahier des charges StockCare (Phase 1) :
le Vehicle Routing Problem priorisé, formulé en MILP et résolu avec PuLP/CBC.

Pilote : **Sousse, Tunisie** — 10 pharmacies réelles géolocalisées via TomTom,
dépôt au 45 Rue Safia Farhat (4054).

---

## Démarrage rapide

```bash
py -m pip install pulp plotly networkx numpy matplotlib nbformat pytest

py vrp_sousse_reel.py            # résout le MILP sur les données Sousse
py carte_sousse_interactive.py   # génère la carte + le graphe
py -m pytest test_models.py -v   # lance les tests
```

---

## Fichiers du cœur MILP

### `vrp_milp_pharma.py` — le moteur, fichier principal
Le modèle MILP générique. Contient les dataclasses `Scenario`, `Fleet`,
`Solution`, et les trois fonctions clés :

- `generate_scenario(n)` — génère un scénario aléatoire (pour tester)
- `compute_distance_matrix(coords)` — calcule `d[i,j]` et `t[i,j]`
- `build_and_solve_vrp(...)` — construit et résout le MILP

Implémente les équations (3) à (12) du cahier des charges : objectif à 4 termes
(carburant, retard SLA pondéré par priorité, tie-break, taille de flotte), et
les contraintes de service unique, conservation de flux, activation de flotte,
capacité, chaîne du froid, propagation temporelle Big-M et échéances SLA.

> **Écart assumé avec le PDF.** Le code ajoute la contrainte `Σ_j x_j0k = z_k`
> (retour au dépôt), absente de l'équation (6) du cahier des charges. Sans elle,
> le solveur produit des chemins ouverts : les véhicules partent et ne rentrent
> jamais, ce qui fausse distances et coûts. À corriger dans le document Phase 2.

> **Unités.** Le code travaille en **minutes** ; le PDF pose les temps en heures.
> Cohérent en interne, mais à harmoniser avant tout échange de JSON entre services.

### `vrp_sousse_reel.py` — le pilote sur données réelles
Applique le moteur au cas Sousse. Contient les coordonnées GPS réelles des
10 pharmacies et du dépôt, la calibration routière, et la simulation des commandes.

**Point d'injection de la Layer 2** : la fonction `simuler_commandes()` produit
`pr[i]` (float dans [0,1]) et `cc[i]` (booléen). C'est cette fonction que le
moteur de priorité viendra remplacer — le reste du pipeline ne bouge pas.

Calibration : distances = haversine × **1.60**, vitesse **20.9 km/h**, valeurs
dérivées de 13 itinéraires TomTom réels (et non d'hypothèses génériques).
La fonction `build_matrix_from_tomtom()` est fournie pour la version exacte
par appels API, à utiliser en production.

### `plan_sousse.json` — la sortie, contrat d'interface
Le plan résolu : nœuds avec coordonnées, priorités, SLA et heures d'arrivée,
plus les tournées par véhicule. **C'est le format d'échange entre la Layer 3
et le web** — toute interface future consomme ce JSON.

---

## Visualisation

### `carte_sousse_interactive.py`
Génère les deux fichiers HTML ci-dessous à partir de `plan_sousse.json`.

### `carte_sousse.html`
Les tournées tracées sur un fond de carte réel de Sousse. Survol d'un nœud :
heure d'arrivée, marge SLA, priorité, chaîne du froid, charge.

### `graphe_sousse.html`
Le même réseau en graphe abstrait, style sombre lumineux. Une couleur par véhicule.

### `dashboard_vrp_sousse.html`
Tableau de bord connecté à TomTom : réinterroge le trafic en direct, recalcule
les heures d'arrivée réelles et les compare aux SLA du plan. Alerte si une
livraison en chaîne du froid passe hors délai. *(Version live dans Cowork ;
ce fichier seul ne s'exécute pas hors de l'application.)*

---

## Justification du choix MILP (défense en soutenance)

Ces fichiers répondent à la question « pourquoi pas du Machine Learning ? ».

### `generer_notebook.py` → `comparaison.ipynb`
Génère un notebook comparant, sur un problème de sac à dos, la résolution
exacte MILP et une descente de gradient sur relaxation continue. Montre que
le gradient produit une **borne** mais des solutions **fractionnaires** —
non exécutables : on ne peut pas emporter 0,37 objet.

### `test_models.py`
Trois tests pytest : intégrité binaire de la solution MILP, décroissance stricte
de la loss du gradient, et validité de la borne de relaxation. Les trois passent.

### `preuve_visuelle_vrp.py` → `preuve_visuelle_vrp.png`
Figure pédagogique en deux panneaux : à gauche une tournée MILP binaire et
réalisable, à droite la « toile d'araignée » fractionnaire du gradient.
**Données factices, illustration conceptuelle** — aucun solveur n'est appelé.

### `vrp_solution.html`
Première visualisation, produite sur un scénario aléatoire avant le passage
aux données réelles de Sousse. Conservée à titre d'historique.

---

## Statut des données

| Élément | Statut |
|---|---|
| Coordonnées des pharmacies | **Réelles** — TomTom Places API |
| Adresse du dépôt | **Réelle** — vérifiée par reverse-geocoding |
| Distances et temps de trajet | **Calibrés** sur 13 itinéraires TomTom réels |
| Quantités commandées `q[i]` | Simulées |
| Priorités `pr[i]`, chaîne du froid `cc[i]` | Simulées — en attente Layer 2 |
| Temps de déchargement `σ[i]`, SLA `T[i]` | Simulés |
| Composition de la flotte | Hypothèse (2 réfrigérés + 1 sec) |

À annoncer explicitement en soutenance : la géographie est réelle, les commandes
ne le sont pas encore.

---

## Limite connue : scalabilité

| Pharmacies | Temps de résolution CBC |
|---|---|
| 5 | 0,5 s |
| 8 | 5 s |
| 10 | 28 s |
| 12+ | dépasse le temps interactif |

Le nombre de variables croît en `n²k`. Le MILP exact convient au pilote
(section 7.4 du cahier des charges), pas à la couverture nationale visée
en Année 5. Deux issues : solveur commercial (Gurobi) ou découpage par secteur.

**Conséquence pour l'architecture web** : aucune requête HTTP synchrone ne
survit à 28 secondes. L'optimisation devra être lancée en tâche asynchrone.

---

## Dossiers ignorés

`__pycache__/`, `.pytest_cache/`, `.ipynb_checkpoints/` — caches générés
automatiquement par Python, pytest et Jupyter. Aucun contenu utile, ils se
recréent seuls. À exclure du dépôt Git.

`run_sousse.log` — fichier de log vide, résidu d'un lancement de test.

---

## Résultat courant

```
Statut     : Optimal
Objectif   : 152,56
Distance   : 24,07 km
Véhicules  : 2 sur 3 mobilisés
Retard SLA : 0 minute
Chaîne du froid : les 4 commandes thermosensibles affectées au véhicule réfrigéré
```

---

**Équipe Chaneb+** — StockCare Phase 1
