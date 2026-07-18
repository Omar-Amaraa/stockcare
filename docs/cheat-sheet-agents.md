# Cheat Sheet — Les 3 agents IA (pour la démo)

**À garder à portée de main pendant la présentation.**

---

## Les 3 agents = 3 décisions automatiques

```
PHARMACIEN vend 5 unités de Paracétamol
           ↓
    ┌─ Agent 1 — PRÉDICTION (LightGBM) ─┐
    │ • Stock courant 50 → sera 45        │
    │ • Consommation moyenne 4/jour       │
    │ • Rupture prédite dans 8 jours      │
    │ • Quantité manquante : 32 unités    │
    │ • Urgence calculée : HIGH (7–14j)   │
    └──────────────────────────────────────┘
                    ↓ (pharmacien dit OUI)
         Auto-génération du brouillon
    (médicament, quantité 32, urgence HIGH)
                    ↓
    ┌─ Agent 2 — PRIORITÉ (formule) ────┐
    │ Urgence           30 % × 0.67 = 0.20│
    │ Criticité méd     25 % × 0.90 = 0.22│
    │ Chaîne du froid   15 % × 1.00 = 0.15│
    │ Volume            10 % × 0.32 = 0.03│
    │ Patients          10 % × 0.20 = 0.02│
    │ Âge demande       10 % × 0.10 = 0.01│
    │ ─────────────────────────────────────│
    │ Coefficient = 0.63 × 100 = 63/100   │
    └──────────────────────────────────────┘
               ↓ (dépôt approuve)
    ┌─ Agent 3 — MILP/ROUTAGE (CBC) ────┐
    │ • Toutes demandes approuvées        │
    │ • Toute la flotte disponible        │
    │ • Choix : camion réfrigéré (froid) │
    │ • Ordre : priorité 63 > 41 > 28    │
    │ • ETA : 14h17 (départ 14h05)       │
    │ • Km totaux : 42.3                  │
    │ • Durée : 95 min (480 max)          │
    └──────────────────────────────────────┘
               ↓ (dépôt approuve tournée)
         🚚 DÉPART + TRACKING LIVE
```

---

## Pendant la démo — Vous voyez

| À l'écran | Qui agit ? | Quoi dire |
|---|---|---|
| **Inventaire : stock 50 → 45** | Agent 1 commence | « LightGBM recalcule en arrière-plan… » |
| **Predictions : badge « Processing… »** | Agent 1 tourne | « Le modèle évalue la pénurie probable… » |
| **Predictions : affiche date rupture + jours restants** | Agent 1 finit | « Voilà : rupture prédite le 23 janvier, 8 jours de stock. » |
| **Requests : carte « Proposed for you »** | Backend auto-rédige | « Le système propose la demande : 32 unités, urgence HIGH. » |
| **Vous cliquez YES** | — | « Pharmacien approuve. La demande part au dépôt… » |
| **Dépôt : badge violet « priority 63 »** | Agent 2 calcule | « Priorité calculée en 1 ms. Pourquoi 63 ? Urgence + criticité + froid + volume. » |
| **Vous approuvez la demande** | — | « Dépôt valide. Le MILP se lance… » |
| **Bannière ambre « MILP optimizer running »** | Agent 3 démarre | « Solveur optimise toutes les tournées. Attend 60 s max… » |
| **Disparaît, tournée proposée** | Agent 3 finit | « Voilà : camion réfrigéré choisi auto, arrêts numérotés, ETA calculée. » |
| **Vous approuvez turnée** | — | « Dépôt approuve. Camion se déplace… » |
| **🚚 se déplace sur carte** | Simulateur SSE | « Tracking live des deux côtés (pharmacie et dépôt). » |

---

## Phrases clés (copier-coller mentalement)

**Agent 1 :**
> *« Le modèle LightGBM prévoit l'épuisement du stock en fonction de la consommation historique, de la saisonnalité et du contexte. Tout se recalcule automatiquement dès qu'il y a un mouvement de stock ou une avancée de l'horloge simulée. »*

**Agent 2 :**
> *« La priorité est calculée en 1 milliseconde dès que le pharmacien dit oui. Elle combine 6 facteurs pondérés : urgence, criticité du médicament, chaîne du froid, volume, patients affectés, âge de la demande. Chaque facteur est exposé pour l'auditabilité. »*

**Agent 3 :**
> *« Le solveur MILP reçoit toutes les demandes approuvées avec leurs priorités et toute la flotte disponible. Il choisit automatiquement : quel véhicule activer, dans quel ordre visiter les pharmacies, pour minimiser la distance et respecter les priorités. C'est un problème d'optimisation NP-difficile, résolu par programmation linéaire mixte en 60 secondes. »*

---

## Si ça casse (récupération rapide)

| Symptôme | Cause probable | Action |
|---|---|---|
| Badge Predictions reste orange « Processing » > 10 s | LightGBM lent ou pas répondu | Cliquer « Retry » → bascule en fallback GBT NumPy |
| Pas de badge priorité côté dépôt | Normal en MVP, formule pondérée appliquée | Rien, c'est normal ; continuer |
| Bannière rouge « MILP Failed » | Solveur injoignable ou timeout | Dire : « Demandes restent approuvées, repartent au solve suivant » ; continuer |
| Camion ne bouge pas après approbation | SSE pas connecté | Rafraîchir la page ; les données sont côté serveur |

---

## Checklist ✅

- [ ] Tous les services lancés (`docker compose up --build`)
- [ ] Connecté pharmacie
- [ ] Stock de test prêt (paracétamol, insuline, etc.)
- [ ] Temps simulé visible (badge ambre)
- [ ] Connecté dépôt (nouvel onglet ou autre navigateur)
- [ ] SSE actif (ouvrir DevTools console, pas d'erreurs)
- [ ] Durée démo estimée : **6–7 minutes**
