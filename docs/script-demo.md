# Script de démonstration — StockCare (~6-7 min)

> Les indications entre [crochets] sont des actions à faire à l'écran, pas à lire.

---

## 1. Introduction (45 s)

Bonjour à tous. Aujourd'hui je vais vous présenter **StockCare**, une plateforme qui s'attaque à un problème concret : les ruptures de stock de médicaments en pharmacie.

Aujourd'hui, une pharmacie s'aperçoit souvent trop tard qu'un médicament va manquer. Elle passe commande dans l'urgence, le dépôt traite les demandes sans vision claire des priorités, et les livraisons sont planifiées à la main — ce qui coûte du temps, du carburant et du CO2.

StockCare ferme cette boucle : **prédiction** de la demande côté pharmacie, **priorisation automatique** côté dépôt, et **tournées de livraison optimisées**. Le tout avec le moins de clics possible : le système travaille en arrière-plan, l'humain ne fait que valider.

---

## 2. Connexion (20 s)

[Ouvrir la page de login]

Voici la porte d'entrée de la plateforme. Deux types de comptes : **pharmacie** et **dépôt**. Je commence côté pharmacie.

[Se connecter avec le compte pharmacie]

*Note : l'interface que vous voyez a été redessinée de zéro — design premium, dark mode, animations fluides, responsive sur tous les appareils — mais la logique métier n'a pas changé.*

---

## 3. Côté pharmacie — Dashboard (30 s)

[Dashboard pharmacie]

Dès la connexion, le pharmacien a une vue d'ensemble : nombre de médicaments suivis, stocks bas, **pénuries prédites**, demandes en cours et livraisons.

Remarquez qu'il n'y a aucun bouton « lancer une prédiction » : tout ce que vous voyez se met à jour automatiquement, en temps réel, dès que les données changent.

---

## 4. Inventaire (45 s)

[Aller dans Inventory]

L'inventaire. Pour cette version de test, la saisie est manuelle — à terme, elle sera alimentée directement par le logiciel métier de la pharmacie.

[Montrer la recherche, les jauges de stock]

Chaque ligne montre le stock actuel par rapport au minimum souhaité, avec une jauge visuelle. Les produits en dessous du seuil sont signalés immédiatement.

[Faire un ajustement, par exemple −5 sur un produit]

Je simule quelques ventes… et c'est là que ça devient intéressant : cet ajustement vient de **déclencher automatiquement** un recalcul des prédictions en arrière-plan.

---

## 5. Prédictions automatiques — Agent 1 en action (45 s)

[Aller dans Predictions]

Voici le résultat. **Agent 1 — LightGBM** — un modèle de gradient boosting entraîné sur l'historique de consommation, est en train de tourner en tâche de fond. Il estime, pour chaque médicament : la **date de rupture probable**, le nombre de **jours restants** et la **quantité manquante**.

[Montrer le badge de statut « Up to date » / « Processing »]

Ce badge indique l'état du calcul : ici on voit que le modèle vient de se rafraîchir, avec sa version et l'heure de mise à jour. L'agent ne demande jamais la permission — il s'active **automatiquement** dès qu'il y a un mouvement de stock ou une avancée du temps simulé. Aucun bouton à cliquer.

---

## 6. Demandes de réapprovisionnement (45 s)

[Aller dans Requests]

Et StockCare va plus loin : quand une pénurie est prédite par Agent 1, le système **rédige lui-même la demande** de réapprovisionnement — médicament, quantité (estimée par la pénurie), urgence (calculée à partir de la date de rupture).

[Montrer une proposition]

La seule décision du pharmacien, c'est **oui ou non**. J'accepte celle-ci.

[Cliquer « Yes, send to depot »]

La demande part au dépôt, et **Agent 2 — la priorité** — se déclenche automatiquement… en 1 milliseconde.

---

## 7. Temps simulé (30 s)

[Montrer le panneau ambre « Simulated application time »]

Petit aparté : ce panneau ambre, volontairement distinct du reste de l'interface, permet de **simuler la date et l'heure** de l'application. C'est un outil de test : il permet de vérifier que le modèle prend bien en compte la saisonnalité ou le jour de la semaine. Si j'avance de 7 jours…

[Cliquer « +7 days »]

…les prédictions se recalculent immédiatement avec ce nouveau contexte temporel.

---

## 8. Côté dépôt — Priorités automatiques (60 s)

[Se déconnecter, se connecter avec le compte dépôt]

Passons de l'autre côté : le dépôt. Le dashboard montre les pharmacies rattachées, les nouvelles demandes et les livraisons actives.

[Aller dans Requests, ouvrir la demande envoyée]

Voici la demande envoyée par la pharmacie il y a un instant. Et regardez : elle a **déjà un score de priorité**, calculé automatiquement à son arrivée — de 0 à 100, avec le détail des facteurs : urgence, criticité du médicament, patients affectés…

Le gestionnaire du dépôt n'a donc pas à trier ni à arbitrer à l'aveugle : la file est déjà ordonnée. Sa décision se résume à **approuver** la demande pour la planification.

[Cliquer « Approve for planning »]

---

## 9. Optimisation des tournées (60 s)

[Aller dans Deliveries]

Et l'approbation vient de déclencher la dernière étape automatique : l'**optimiseur de tournées**. C'est un solveur MILP qui choisit le véhicule et l'ordre des arrêts pour minimiser la distance — donc les délais, le carburant et les émissions de CO2.

[Montrer la proposition de route : véhicule, arrêts, km, durée]

Voici sa proposition : véhicule sélectionné, séquence des pharmacies, distance et durée totales. Là encore, l'humain ne fait que valider : **approuver et expédier**, ou refuser.

[Cliquer « Approve & dispatch », puis montrer la carte]

Une fois expédiée, la livraison est suivie **en temps réel** sur la carte : position du véhicule, progression, ETA.

---

## 10. Retour pharmacie + conclusion (45 s)

[Optionnel : revenir sur le compte pharmacie, page Deliveries]

Côté pharmacie, on retrouve exactement la même livraison, suivie en direct jusqu'à la réception.

Pour conclure : StockCare, c'est une chaîne complète — **prédire, prioriser, livrer** — où chaque étape se déclenche automatiquement et où l'humain ne prend que les décisions qui comptent : oui ou non.

Les bénéfices : moins de ruptures pour les patients, moins d'urgences pour les pharmaciens, et des tournées optimisées — donc moins de coûts, de carburant et de CO2.

Prochaines étapes : l'intégration directe avec les ERP des pharmacies pour supprimer la saisie manuelle, et l'enrichissement du modèle de prédiction. Merci de votre attention, je suis ouvert à vos questions.

---

### Notes de secours (si problème pendant la démo)

- Si une prédiction met du temps : « Le calcul tourne en arrière-plan, le badge passera à jour tout seul — c'est justement le principe : non bloquant. »
- Si le SSE/temps réel ne suit pas : rafraîchir la page, les données sont côté serveur.
- Ordre de repli : montrer d'abord le dépôt avec des données déjà présentes, puis dérouler côté pharmacie.
