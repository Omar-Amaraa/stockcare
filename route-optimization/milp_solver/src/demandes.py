"""
=============================================================================
 DEMANDES PHARMACIE -> SCENARIO MILP
=============================================================================
 Modele metier :
   Chaque pharmacie emet autant de LIGNES DE COMMANDE qu'elle a de
   medicaments manquants (une ligne = un num_amm + une quantite).
   Le depot recoit l'ensemble et doit decider d'une tournee optimale.

 Le MILP raisonne au niveau du NOEUD (une pharmacie = un arret), pas de la
 ligne. Ce module effectue donc l'agregation ligne -> noeud :

   pr[i]    = agregation des priorites des medicaments demandes  (defaut: max)
              -> l'article le plus critique dicte l'urgence de l'arret
   cc[i]    = 1 si AU MOINS UN medicament demande est thermosensible
   q[i]     = somme des quantites demandees
   sigma[i] = forfait + increment par ligne (plus de lignes = plus long)
   T[i]     = fenetre SLA interpolee depuis pr[i], resserree si chaine du froid

 Auteur : Adam — Projet MILP CARE
=============================================================================
"""

from __future__ import annotations

import csv
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Sequence

from catalogue import Catalogue, Medicament


# =============================================================================
# 1. STRUCTURES
# =============================================================================


@dataclass
class LigneCommande:
    """Un medicament manquant demande par une pharmacie."""

    num_amm: str
    quantite: float
    medicament: Medicament

    @property
    def priorite(self) -> float:
        return self.medicament.priorite

    @property
    def chaine_du_froid(self) -> int:
        return self.medicament.chaine_du_froid


@dataclass
class CommandePharmacie:
    """L'ensemble des lignes emises par une pharmacie pour ce cycle."""

    pharmacie_id: str
    nom: str
    lignes: List[LigneCommande] = field(default_factory=list)

    # --- Agregats consommes par le MILP ------------------------------------

    def priorite(self, mode: str = "max") -> float:
        """Score de priorite du NOEUD, agrege depuis les lignes."""
        if not self.lignes:
            return 0.0
        prios = [l.priorite for l in self.lignes]
        if mode == "max":
            return max(prios)
        if mode == "moyenne":
            return sum(prios) / len(prios)
        if mode == "moyenne_ponderee":
            poids = sum(l.quantite for l in self.lignes)
            if poids <= 0:
                return sum(prios) / len(prios)
            return sum(l.priorite * l.quantite for l in self.lignes) / poids
        raise ValueError(
            f"mode d'agregation inconnu : {mode!r} "
            f"(attendu : max | moyenne | moyenne_ponderee)"
        )

    @property
    def chaine_du_froid(self) -> int:
        """1 des qu'une seule ligne est thermosensible : tout le colis suit."""
        return 1 if any(l.chaine_du_froid for l in self.lignes) else 0

    @property
    def charge(self) -> float:
        return round(sum(l.quantite for l in self.lignes), 1)

    @property
    def nb_lignes(self) -> int:
        return len(self.lignes)

    def article_critique(self) -> Medicament | None:
        """Le medicament qui porte la priorite du noeud (tracabilite)."""
        if not self.lignes:
            return None
        return max(self.lignes, key=lambda l: l.priorite).medicament


# =============================================================================
# 2. DERIVATION SLA ET TEMPS DE SERVICE
# =============================================================================


def fenetre_sla(priorite: float, chaine_du_froid: int, cfg_sla: Dict) -> float:
    """Interpole la deadline SLA (minutes) depuis la priorite agregee.

    Interpolation lineaire entre deux ancrages metier :
        priorite_basse -> fenetre_basse_min   (commande de confort)
        priorite_haute -> fenetre_haute_min   (urgence vitale)
    Les priorites hors bornes sont ecretees, jamais extrapolees.
    """
    p_bas = cfg_sla["priorite_basse"]
    p_haut = cfg_sla["priorite_haute"]
    f_bas = cfg_sla["fenetre_basse_min"]
    f_haut = cfg_sla["fenetre_haute_min"]

    if p_haut <= p_bas:
        raise ValueError("config sla : priorite_haute doit exceder priorite_basse")

    p = min(max(priorite, p_bas), p_haut)
    ratio = (p - p_bas) / (p_haut - p_bas)
    fenetre = f_bas + ratio * (f_haut - f_bas)

    if chaine_du_froid:
        fenetre += cfg_sla.get("bonus_chaine_froid_min", 0.0)

    return round(max(fenetre, cfg_sla.get("plancher_min", 30.0)), 1)


def temps_service(nb_lignes: int, cfg_dech: Dict) -> float:
    """Temps de dechargement sigma[i] (minutes), croissant avec le nb de lignes."""
    brut = cfg_dech["forfait_min"] + nb_lignes * cfg_dech["par_ligne_min"]
    return round(min(brut, cfg_dech["plafond_min"]), 1)


# =============================================================================
# 3. CHARGEMENT DES DEMANDES REELLES
# =============================================================================


COLONNES_DEMANDES = {"pharmacie_id", "num_amm", "quantite"}


def charger_demandes(
    chemin_csv: Path | str,
    catalogue: Catalogue,
    noms_pharmacies: Dict[str, str],
    *,
    ignorer_amm_inconnus: bool = False,
) -> Dict[str, CommandePharmacie]:
    """Lit demandes.csv (pharmacie_id, num_amm, quantite) et resout le catalogue.

    Args:
        chemin_csv: fichier des lignes de commande emises par les pharmacies.
        catalogue: referentiel des priorites.
        noms_pharmacies: pharmacie_id -> libelle affichable.
        ignorer_amm_inconnus: si True, saute les AMM absents du referentiel au
            lieu de lever une erreur (utile en integration progressive).

    Returns:
        pharmacie_id -> CommandePharmacie.
    """
    chemin_csv = Path(chemin_csv)
    with open(chemin_csv, "r", encoding="utf-8-sig", newline="") as f:
        lecteur = csv.DictReader(f)
        manquantes = COLONNES_DEMANDES - set(lecteur.fieldnames or [])
        if manquantes:
            raise ValueError(
                f"{chemin_csv.name} : colonnes manquantes {sorted(manquantes)}"
            )
        lignes = list(lecteur)

    commandes: Dict[str, CommandePharmacie] = {}
    inconnus: List[str] = []

    for n, ligne in enumerate(lignes, start=2):
        pid = str(ligne["pharmacie_id"]).strip()
        amm = str(ligne["num_amm"]).strip()
        try:
            qte = float(ligne["quantite"])
        except (TypeError, ValueError) as exc:
            raise ValueError(
                f"{chemin_csv.name} ligne {n} : quantite illisible "
                f"({ligne.get('quantite')!r})"
            ) from exc
        if qte <= 0:
            continue

        try:
            med = catalogue.par_amm(amm)
        except KeyError:
            if ignorer_amm_inconnus:
                inconnus.append(amm)
                continue
            raise ValueError(
                f"{chemin_csv.name} ligne {n} : num_amm {amm!r} absent du "
                f"catalogue. Relancer avec ignorer_amm_inconnus=True pour "
                f"tolerer les references non referencees."
            ) from None

        if pid not in commandes:
            commandes[pid] = CommandePharmacie(
                pharmacie_id=pid, nom=noms_pharmacies.get(pid, pid)
            )
        commandes[pid].lignes.append(
            LigneCommande(num_amm=amm, quantite=qte, medicament=med)
        )

    if inconnus:
        print(f"  [!] {len(inconnus)} ligne(s) ignoree(s) : AMM hors catalogue "
              f"(ex. {inconnus[:3]})")

    return commandes


# =============================================================================
# 4. SIMULATION DE DEMANDES (si aucun flux reel n'est branche)
# =============================================================================


def simuler_demandes(
    catalogue: Catalogue,
    pharmacies: Sequence[Dict],
    cfg_sim: Dict,
) -> Dict[str, CommandePharmacie]:
    """Genere des lignes de commande plausibles pour chaque pharmacie.

    Le tirage est uniforme sur le catalogue : la diversite des priorites
    provient donc directement du referentiel, pas d'un profil code en dur.
    """
    rng = random.Random(cfg_sim.get("seed"))
    meds = catalogue.medicaments
    commandes: Dict[str, CommandePharmacie] = {}

    for pharma in pharmacies:
        pid = str(pharma["id"])
        cmd = CommandePharmacie(pharmacie_id=pid, nom=pharma["nom"])
        nb = rng.randint(
            cfg_sim["lignes_min_par_pharmacie"],
            cfg_sim["lignes_max_par_pharmacie"],
        )
        for _ in range(nb):
            med = rng.choice(meds)
            qte = round(
                rng.uniform(
                    cfg_sim["quantite_min_par_ligne"],
                    cfg_sim["quantite_max_par_ligne"],
                ),
                1,
            )
            cmd.lignes.append(
                LigneCommande(num_amm=med.num_amm, quantite=qte, medicament=med)
            )
        commandes[pid] = cmd

    return commandes


def exporter_demandes(commandes: Dict[str, CommandePharmacie], chemin: Path | str) -> None:
    """Ecrit les demandes au format attendu par charger_demandes()."""
    chemin = Path(chemin)
    chemin.parent.mkdir(parents=True, exist_ok=True)
    with open(chemin, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["pharmacie_id", "num_amm", "quantite", "nom_specialite", "priorite"])
        for cmd in commandes.values():
            for l in cmd.lignes:
                w.writerow([
                    cmd.pharmacie_id, l.num_amm, l.quantite,
                    l.medicament.nom_specialite, f"{l.priorite:.6f}",
                ])


# =============================================================================
# 5. ASSEMBLAGE DU SCENARIO
# =============================================================================


def construire_scenario(
    depot: Dict,
    pharmacies: Sequence[Dict],
    commandes: Dict[str, CommandePharmacie],
    config: Dict,
):
    """Assemble coordonnees reelles + commandes agregees en un Scenario MILP.

    Returns:
        (scenario, noms, commandes_ordonnees) ou commandes_ordonnees[i]
        correspond au noeud i du scenario (index 0 = depot -> None).
    """
    from vrp_milp_pharma import Scenario  # import tardif : evite un cycle

    mode = config["priorite"]["agregation"]
    cfg_sla = config["sla"]
    cfg_dech = config["dechargement"]

    coords = [(depot["lon"], depot["lat"])]
    noms = [depot["nom"]]
    q: List[float] = [0.0]
    cc: List[int] = [0]
    pr: List[float] = [0.0]
    sigma: List[float] = [0.0]
    T: List[float] = [0.0]
    cmds: List[CommandePharmacie | None] = [None]

    for pharma in pharmacies:
        pid = str(pharma["id"])
        cmd = commandes.get(pid)
        if cmd is None or not cmd.lignes:
            continue  # aucune commande : la pharmacie n'est pas un arret

        coords.append((pharma["lon"], pharma["lat"]))
        noms.append(pharma["nom"])
        p = cmd.priorite(mode)
        froid = cmd.chaine_du_froid

        q.append(cmd.charge)
        cc.append(froid)
        pr.append(round(p, 4))
        sigma.append(temps_service(cmd.nb_lignes, cfg_dech))
        T.append(fenetre_sla(p, froid, cfg_sla))
        cmds.append(cmd)

    if len(coords) < 2:
        raise ValueError(
            "Aucune pharmacie n'a de commande : rien a router. "
            "Verifier data/demandes.csv."
        )

    scenario = Scenario(coords=coords, q=q, cc=cc, pr=pr, sigma=sigma, T=T)
    return scenario, noms, cmds
