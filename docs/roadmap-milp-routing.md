> **Mise à jour 2 (19/07/2026, plus tard) — routage temps réel implémenté** : (a) le §2.1 est corrigé — `application.yml` prend maintenant `external` + `auto-route=true` par défaut avec URLs `localhost:8000/8002`, donc le vrai MILP tourne aussi hors Docker Compose ; (b) **re-planification temps réel** ajoutée (`RouteWorkflowService`, flag `stockcare.workflow.auto-route-replan`, défaut `true`) : quand une nouvelle demande approuvée arrive alors que des propositions de tournées attendent encore la décision du dépôt, leurs demandes sont repliées dans un unique re-solve fleet-wide, et les propositions périmées ne sont retirées qu'**après** un solve réussi (un optimiseur injoignable ne laisse jamais le dépôt sans proposition). Les livraisons expédiées (camion parti) ne sont jamais rappelées. Vérifié par revue croisée compile-level (imports, signatures, beans conditionnels, interactions simulateur) — la compilation Maven réelle reste à faire (§2.2 toujours ouvert).
>
> **Mise à jour du 19/07/2026** : le correctif §1 (géométrie à paliers) est implémenté (`milp_care/milp_care_delivery/src/geometrie.py::build_matrix_tiered`, branché dans `routing-service/solver.py`, config dans `routing-service/config.json → geometrie.paliers`). Validé en relançant le solveur réel sur le dépôt + les 5 pharmacies seedées : **avant** le correctif, le solve entier était `INFEASIBLE` (les 5 demandes non servies, y compris Tunis et Ariana qui sont à quelques km) — **après**, `OPTIMAL`, 5/5 arrêts servis sur 2 véhicules, avec un retard SLA explicite et honnête sur Sousse/Sfax (les fenêtres SLA, elles, restent calibrées pour une échelle urbaine et n'ont pas encore été retravaillées pour le national — reste un point ouvert). Les 5 comptes pharmacie (`ph.tunis`, `ph.ariana`, `ph.nabeul`, `ph.sousse`, `ph.sfax`) existaient déjà dans le seed ; ils sont maintenant documentés dans le README. Test de non-régression ajouté : `routing-service/tests/test_routes.py::test_distant_pharmacy_stays_routable_with_tiered_geometry`.

# Roadmap — Layer 3 (MILP Routing) : de la démo statique au routage temps réel national

*Audit du 19/07/2026 — basé sur le CDC (Phase 1), le README, et lecture du code (`routing-service/`, `milp_care/`, `backend/src/main/java/.../route`, `.../delivery`, `frontend/src/app/features/depot`).*

## 0. Verdict — ce n'est pas "collé sans rien derrière"

Le câblage backend → routing-service → MILP → frontend est **réellement implémenté**, pas un mock déguisé :

- `RequestApprovedForPlanning` est un vrai événement Spring (`@TransactionalEventListener(AFTER_COMMIT)`), il appelle `RouteWorkflowService.autoPlan()` qui verrouille par dépôt et fusionne les approbations concurrentes en un seul solve — exactement ce que décrit le README.
- `ExternalRouteOptimizationService` construit un payload correct (dépôt + arrêts avec lat/lon réelles + flotte avec capacité/froid) et appelle `POST /optimize` sur `routing-service`.
- `routing-service/solver.py` → `milp_path.py` → `vrp_milp_pharma.py` est un vrai MILP PuLP/CBC avec les contraintes du CDC (capacité, chaîne du froid, fenêtres SLA, élimination de sous-tours par Big-M temporel).
- Le frontend dépôt (`depot-deliveries.component.ts` + `tracking-map.component.ts`) affiche une vraie carte Leaflet, un badge "MILP optimal" vs "heuristic", et suit le SSE de `WorkflowBroadcaster`.
- `docker-compose.yml` force `STOCKCARE_MODEL_ROUTE_MODE=external` et `STOCKCARE_MODEL_PREDICTION_MODE=external` par défaut.

**Le vrai problème n'est pas l'absence de câblage, c'est que le MILP reçoit des distances/temps de trajet faux dès qu'une pharmacie n'est pas à côté du dépôt.** C'est ça qui casse le cas d'usage "dépôt reçoit des demandes de pharmacies dans toute la Tunisie" — et c'est réparable en un point précis. Détail ci-dessous.

---

## 1. Le bug qui bloque le cas d'usage national (P0)

**Fichier : `milp_care/milp_care_delivery/src/geometrie.py`, fonction `build_matrix_calibrated`, utilisée par `routing-service/milp_path.py`.**

La matrice distance/temps envoyée au solveur n'est *pas* une vraie distance routière ni un appel API temps réel : c'est `haversine(i,j) × 1.6 (sinuosité) ÷ 20.9 km/h (vitesse effective)`. Ces deux constantes viennent de **13 trajets réels mais tous courts, mesurés à Sousse** (`config.json → geometrie._comment`). Elles sont appliquées telles quelles à *toutes* les paires dépôt↔pharmacie, y compris sur 200+ km.

Conséquence chiffrée, avec `H_max = 480 min` (8h, `config.json → solveur.H_max`) : un aller-retour dépôt→pharmacie→dépôt ne tient dans l'horizon que si la distance à vol d'oiseau dépôt-pharmacie est **≤ ~52 km** (`480 × 20.9 / (2 × 1.6 × 60)`), et encore, sans compter les autres arrêts de la tournée ni le déchargement.

Appliqué aux 5 pharmacies actuellement seedées dans `DevDataSeeder.java` (dépôt = Tunis 36.80/10.18) :

| Pharmacie | Distance à vol d'oiseau | Routable dans l'horizon actuel ? |
|---|---|---|
| Pharmacie Centrale Tunis | ~1 km | Oui |
| El Menzah (Ariana) | ~8 km | Oui |
| Nabeul Centre | ~63 km | **Non — juste hors limite** |
| Sousse Medina | ~140 km | **Non, largement infaisable** |
| Sfax Ville | ~235 km | **Non — ~18h de trajet estimé pour une seule liaison, contre un horizon de 8h** |

Autrement dit : **3 des 5 pharmacies de démo sont mathématiquement impossibles à servir** avec la config actuelle. Le solveur ne "plante" pas au sens propre : il renvoie soit `INFEASIBLE`, soit une solution qui exclut ces arrêts (`unfulfilledRequestIds`) — ce qui, vu du dépôt, ressemble exactement à "l'IA ne fait rien pour ces pharmacies-là". C'est très probablement la source concrète de l'impression "l'agent 3 ne marche pas vraiment".

Deux fonctions existent déjà dans le même fichier mais ne sont **jamais appelées par le pipeline réel** :
- `build_matrix_from_tomtom()` — appel de la vraie API TomTom Routing, mentionné comme "à utiliser en production" mais jamais branché.
- `compute_distance_matrix()` dans `vrp_milp_pharma.py` — variante générique avec vitesse/facteur configurables, utilisée seulement dans le bloc `if __name__ == "__main__"` de démonstration locale, pas dans le service HTTP.

### Correction proposée (priorité 1)

1. **Court terme (1-2 jours) — remplacer la vitesse unique par un modèle à paliers selon la distance.** Une vitesse urbaine (~21-25 km/h, garder la calibration Sousse pour le dernier km) pour les trajets courts, une vitesse autoroute/route nationale (~80-90 km/h, cohérent avec le réseau tunisien) au-delà d'un seuil (ex. 30 km). Simple à coder dans `build_matrix_calibrated`, ne demande aucune clé API, corrige immédiatement Sfax/Sousse/Nabeul.
2. **Moyen terme — brancher `build_matrix_from_tomtom()` réellement**, avec un cache (les distances dépôt-pharmacie changent rarement, seule la position des pharmacies varie) pour ne pas payer `n×(n-1)` appels API à chaque solve. C'est la vraie réponse à "je veux que le MILP dépende de la position réelle des pharmacies" : aujourd'hui, ce n'est qu'une approximation géométrique, jamais une distance routière réelle.
3. Si aucun budget API : héberger un **OSRM local** (gratuit, pas de clé, table de distances par lots) sur les données OpenStreetMap de la Tunisie — c'est probablement le meilleur rapport effort/robustesse pour un projet étudiant, et ça reste "temps réel" (quelques millisecondes par requête, un seul processus Docker de plus dans le compose).
4. Revoir `H_max` : 480 min est cohérent pour une tournée locale sur une seule demi-journée, mais incompatible avec un dépôt unique censé couvrir tout le pays. Soit on assume plusieurs dépôts régionaux (cohérent avec le §7.4 "Phased rollout" du CDC — un dépôt, une région, pour le pilote), soit on assume un horizon plus long avec découchés/relais. **Décision produit à trancher avant de corriger la géométrie**, voir §4.

---

## 2. Autres lacunes réelles sur Layer 3, par ordre d'impact

| # | Constat | Fichier(s) | Impact | Effort |
|---|---|---|---|---|
| 2.1 | `stockcare.models.route-optimization.mode` vaut **`mock`** par défaut dans `application.yml`. Seul `docker-compose.yml` force `external`. Tout lancement hors Compose (IDE, `mvn spring-boot:run`, tests manuels) retombe silencieusement sur `MockRouteOptimizationService` (nearest-neighbour glouton, pas de MILP) sans erreur visible à l'écran. | `backend/src/main/resources/application.yml:82` | Élevé — explique une bonne partie de "j'ai l'impression que rien n'utilise l'IA" si le backend a déjà tourné hors Docker | Faible — inverser le défaut, ou logguer un warning explicite au démarrage si mode=mock |
| 2.2 | Le backend Java **n'a jamais été compilé/démarré de bout en bout** avec JDK/Maven réel (admis dans le README, §7, "peut révéler des erreurs de compilation... validation Hibernate au boot"). Si l'appli "ne marche pas" au sens large, ça peut littéralement être un crash au démarrage, pas un problème d'IA. | — | Bloquant potentiel | À vérifier en premier, avant tout le reste |
| 2.3 | Les 5 pharmacies sont **codées en dur dans `DevDataSeeder.java`**. Aucun flux de création de pharmacie côté frontend ne capture lat/lon (recherche `latitude|longitude` dans `frontend/src/app` : seulement `tracking-map.component.ts` et `models.ts`, pas de formulaire de création/géocodage). "Pharmacies dans toute la Tunisie" est aujourd'hui un jeu de 5 lignes statiques, pas un réseau qui grandit. | `backend/.../config/seed/DevDataSeeder.java` | Moyen — bloque la démo "nouvelle pharmacie qui rejoint le réseau" | Moyen — formulaire + géocodage (Nominatim/OSM gratuit) |
| 2.4 | `pri` (coefficient de priorité) envoyé au MILP vient de `MockPriorityCalculationService` — formule pondérée statique, cohérent avec le CDC ("Phase 1 : `pri` constante"), mais `ExternalPriorityCalculationService` est explicitement "Not implemented in the MVP". Le routage lui-même est réel, mais un des trois poids qu'il optimise (la priorité) n'est pas encore un signal dynamique. | `backend/.../priority/service/ExternalPriorityCalculationService.java` | Faible pour l'instant — hors scope Phase 1 assumé par le CDC | Volontairement reporté, cf. CDC §3.2 |
| 2.5 | `DeliveryService.coord()` retombe silencieusement sur `(0,0)` (Golfe de Guinée) si une pharmacie/dépôt a une lat/lon nulle, au lieu de lever une erreur. Pas déclenché aujourd'hui (le seed est complet) mais deviendra un piège dès que 2.3 est corrigé et qu'une pharmacie est créée sans coordonnées. | `backend/.../delivery/service/DeliveryService.java:453-455` | Faible aujourd'hui, latent | Trivial — lever une exception au lieu de défaut silencieux |
| 2.6 | Un seul dépôt existe (`Depot Central Tunis`). Pas de logique "quel dépôt sert quelle pharmacie" si plusieurs dépôts coexistent. Cohérent avec le pilote CDC (§7.4 : un dépôt, une région), mais en tension directe avec l'énoncé "dépôt reçoit des demandes de partout en Tunisie" — voir §4. | `DevDataSeeder.java` | Décision produit | — |

---

## 3. Le "temps réel" existe déjà — ce n'est pas ce qui manque

Contrairement à l'hypothèse initiale, la partie *déclenchement* est déjà correcte et n'a pas besoin d'être reconstruite :

- Chaque approbation dépôt déclenche un solve **immédiat**, asynchrone, verrouillé par dépôt (les approbations simultanées fusionnent en un seul solve au lieu de se marcher dessus).
- Le solveur a une limite de 60 s (`config.json → solveur.time_limit`), donc la réponse reste "temps réel" au sens interactif.
- La géométrie est recalculée à chaque appel à partir des coordonnées reçues (`build_matrix_calibrated` est appelé dans `build_problem()` à chaque requête, pas mis en cache/précalculé) — donc si une pharmacie change de position ou qu'une nouvelle apparaît, le prochain solve en tient compte automatiquement.

Ce qui manque, ce n'est pas la fréquence de résolution, c'est **la fidélité de la distance utilisée** (§1) et **l'échelle géographique que le modèle peut couvrir** (§4).

---

## 4. Décision à prendre avant de coder : quelle est la vraie portée du "dépôt" ?

Le CDC (§7.4, rollout) décrit un pilote à *un* dépôt et *une* région. Le comportement demandé ("le dépôt reçoit des demandes de pharmacies dans toute la Tunisie") suppose une couverture nationale depuis un point unique. Les deux ne sont pas incompatibles, mais ils impliquent des choix différents pour la correction du §1 :

- **Option A — un seul dépôt, couverture nationale.** Il faut un `H_max` réaliste (ou plusieurs créneaux/jour) et des vitesses route réelles (§1, palier autoroute). Une tournée Tunis→Sfax→Tunis en une demi-journée est possible sur autoroute (~5h de route), mais pas avec `H_max=480` si on ajoute plusieurs arrêts et du déchargement — le modèle devra peut-être accepter des tournées multi-jours ou des "hubs" intermédiaires.
- **Option B — plusieurs dépôts régionaux, chacun sert son bassin.** Plus fidèle au CDC et à la calibration Sousse existante. Demande une étape d'affectation pharmacie→dépôt (le plus proche, ou par capacité) avant le MILP, mais le MILP lui-même n'a pas besoin de changer.

**Recommandation** : traiter Option B comme cible produit réaliste pour la suite du hackathon (correspond au CDC), tout en corrigeant le §1 pour qu'un dépôt puisse au moins *raisonnablement* couvrir sa région sans exclure des pharmacies à 60-140 km (Sousse, Nabeul sont dans un rayon plausible pour un dépôt basé à Tunis ou Sousse). Sfax reste hors de portée d'un seul dépôt-Tunis quel que soit le modèle de vitesse — ça confirme qu'il faudra un deuxième dépôt pour la démo si Sfax doit être servie.

---

## 5. Plan d'action, dans l'ordre

1. **Vérifier que le backend compile et démarre réellement** (`mvn -f backend clean verify`, puis `docker compose up --build`, tous les services verts). Si erreur Hibernate/HQL au boot, la corriger avant tout le reste — c'est peut-être la vraie cause de "l'app ne marche pas".
2. **Confirmer, dans les logs du backend démarré, que `route-optimization.mode=external` est bien actif** (endpoint `/actuator` ou log de démarrage), pas retombé sur `mock` (§2.1).
3. ~~**Corriger la vitesse/sinuosité à paliers** dans `geometrie.py`~~ — **fait le 19/07/2026**, voir la mise à jour en tête de document.
4. ~~**Relancer un solve avec les 5 pharmacies seedées et vérifier dans la réponse `/optimize` qu'aucune n'est dans `unfulfilledRequestIds`**~~ — **fait**, `test_distant_pharmacy_stays_routable_with_tiered_geometry` ajouté à `routing-service/tests/test_routes.py`.
5. Trancher Option A vs B (§4) et ajuster `H_max`/nombre de dépôts en conséquence.
6. Brancher une vraie source de distance (OSRM local ou TomTom, §1 moyen terme) une fois le modèle à paliers validé — améliore la précision sans changer l'architecture.
7. Ouvrir la création de pharmacie côté frontend avec capture d'adresse → géocodage (§2.3), pour que "pharmacies dans toute la Tunisie" cesse d'être 5 lignes figées.
8. Durcir `DeliveryService.coord()` pour lever une erreur explicite sur coordonnées manquantes (§2.5) — trivial, à faire en même temps que 7.
9. Seulement après tout ça : envisager de sortir Layer 2 (priorité) du mode mock — explicitement hors scope Phase 1 selon le CDC, ne pas le prioriser avant que le routage soit fiable sur des distances réelles.

---

## 6. Ce qu'il ne faut *pas* refaire

- Ne pas réécrire `vrp_milp_pharma.py` : la formulation MILP (contraintes, objectif, élimination de sous-tours) correspond fidèlement à la formulation mathématique du CDC (§3.3 / Layer 3). Le problème est en amont (les données qu'on lui donne), pas dans le modèle lui-même.
- Ne pas retoucher la chaîne d'événements Spring (`RequestApprovedForPlanning` → `RouteWorkflowService` → `ExternalRouteOptimizationService`) : elle est correcte et correspond au comportement "temps réel" recherché.
- Ne pas construire un nouveau composant de carte : `tracking-map.component.ts` + SSE fonctionnent déjà.
