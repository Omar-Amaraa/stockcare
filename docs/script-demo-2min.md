# Script ultra-rapide : Démo 2 minutes

**Destiné à ceux qui ont peu de temps. Focus sur les 3 agents et le "oui/non" des humains.**

---

## ~0:00 | Intro (20 s)

StockCare automatise la chaîne pharmacie → dépôt → livraison. Trois agents IA prennent les décisions ; l'humain ne dit que oui ou non.

[Ouvrir login]

---

## ~0:20 | Agent 1 — Prédiction (30 s)

[Connecté pharmacie. Aller Inventory.]

Je baisse le stock de paracétamol de 50 à 45 unités.

[Aller Predictions]

**Agent 1 — LightGBM —** prévoit automatiquement : rupture dans 8 jours, 32 unités manquantes.

[Montrer le badge « Up to date »]

Aucun clic pour lancer le calcul. Tout se recalcule dès qu'il y a un mouvement de stock.

---

## ~0:50 | Pharmacien oui/non (20 s)

[Aller Requests]

Le système propose : « Envoyer 32 unités au dépôt ? »

[Cliquer YES]

Pharmacien dit oui. La demande part.

---

## ~1:10 | Agent 2 — Priorité (20 s)

[Connecté dépôt. Aller Requests.]

La demande arrive. **Agent 2** a calculé sa priorité en 1 ms : **62/100**.

Pourquoi 62 ? Urgence (30%) + criticité (25%) + froid (15%) + volume (10%) + patients (10%) + âge (10%).

Tous les facteurs sont visibles. Pas de magie noire.

---

## ~1:30 | Dépôt approuve (20 s)

[Cliquer « Approve for planning »]

Dépôt approuve la demande.

[Aller Deliveries]

[Montrer bannière « MILP optimizer running »]

---

## ~1:50 | Agent 3 — MILP (40 s)

**Agent 3** — le solveur **MILP** — reçoit toutes les demandes approuvées et toute la flotte. Il choisit :
- Quel camion ?
- Quel ordre d'arrêts ?
- Respectant les priorités et minimisant la distance.

[Montrer la tournée proposée : camion réfrigéré, arrêts numérotés, km/durée]

Remarquez : le **camion réfrigéré** a été choisi par le solveur parce qu'il y a du froid. Pas de main humaine, pas de choix arbitraire.

[Cliquer « Approve & dispatch »]

[Montrer le camion qui se déplace sur la carte]

Dépôt et pharmacie voient la livraison en temps réel. SSE push, mises à jour toutes les 2 s.

---

## ~2:30 | Conclusion (Bonus, si temps)

**3 agents, 1 chaîne.**
- Agent 1 prédit (LightGBM)
- Agent 2 priorise (formule pondérée)
- Agent 3 optimise la logistique (MILP)

L'humain : oui → non, approuver → refuser.

Résultats : moins de ruptures, moins d'urgences, moins de carburant, moins de CO2.

**Merci. Questions ?**

---

### Checklist rapide ✅

- [ ] Services verts (frontend 4200, backend 8080, prediction 8000, routing 8002)
- [ ] Comptes seed chargés
- [ ] Stock préparé (paracétamol ou insuline)
- [ ] SSE actif (pas de lag entre onglets)
