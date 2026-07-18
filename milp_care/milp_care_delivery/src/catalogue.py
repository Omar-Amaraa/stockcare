"""
=============================================================================
 CATALOGUE MEDICAMENTEUX — source des priorites dynamiques
=============================================================================
 Remplace les anciens PROFILS a priorite figee (6 valeurs codees en dur) par
 le referentiel complet : un coefficient de priorite PAR MEDICAMENT, charge
 depuis pharmacy_delivery_priorities.csv.

 Colonnes attendues :
   num_amm, nom_specialite, dosage, forme, dci, priority

 Le flag chaine du froid n'existe pas dans le CSV : il est deduit par regles
 (config/cold_chain_rules.json), editables sans toucher au code.

 Auteur : Adam — Projet MILP CARE
=============================================================================
"""

from __future__ import annotations

import csv
import json
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List


# =============================================================================
# 1. NORMALISATION TEXTE
# =============================================================================


def normaliser(texte: str) -> str:
    """Minuscules, sans accents, espaces compactes — pour un matching robuste."""
    if not texte:
        return ""
    decompose = unicodedata.normalize("NFKD", texte)
    sans_accent = "".join(c for c in decompose if not unicodedata.combining(c))
    return " ".join(sans_accent.lower().split())


# =============================================================================
# 2. STRUCTURE D'UN MEDICAMENT
# =============================================================================


@dataclass(frozen=True)
class Medicament:
    """Une ligne du referentiel AMM, enrichie du flag chaine du froid."""

    num_amm: str
    nom_specialite: str
    dosage: str
    forme: str
    dci: str
    priorite: float          # coefficient dynamique [0,1] issu du CSV
    chaine_du_froid: int     # 1 si thermosensible (deduit par regles)

    def __str__(self) -> str:
        froid = " [FROID]" if self.chaine_du_froid else ""
        return f"{self.nom_specialite} {self.dosage} (pr={self.priorite:.3f}){froid}"


# =============================================================================
# 3. REGLES CHAINE DU FROID
# =============================================================================


class ReglesChaineFroid:
    """Determine si un medicament exige le transport refrigere (2-8 C)."""

    def __init__(self, motifs_dci: Iterable[str], motifs_forme: Iterable[str]):
        self.motifs_dci = [normaliser(m) for m in motifs_dci if m]
        self.motifs_forme = [normaliser(m) for m in motifs_forme if m]

    @classmethod
    def depuis_json(cls, chemin: Path | str) -> "ReglesChaineFroid":
        with open(chemin, "r", encoding="utf-8") as f:
            data = json.load(f)
        return cls(data.get("motifs_dci", []), data.get("motifs_forme", []))

    def evaluer(self, dci: str, forme: str, nom_specialite: str = "") -> int:
        """Retourne 1 si thermosensible, 0 sinon.

        On balaie la DCI ET le nom commercial : de nombreux vaccins portent
        une DCI codifiee non evocatrice (ex. COMIRNATY / raxtozinameran),
        que seul le nom de specialite permet de rattraper.
        """
        d = normaliser(dci)
        f = normaliser(forme)
        n = normaliser(nom_specialite)
        if any(m in d or m in n for m in self.motifs_dci):
            return 1
        if any(m in f for m in self.motifs_forme):
            return 1
        return 0


# =============================================================================
# 4. CATALOGUE
# =============================================================================


class Catalogue:
    """Referentiel indexe par num_amm, avec recherche par nom et par DCI."""

    COLONNES_REQUISES = {
        "num_amm", "nom_specialite", "dosage", "forme", "dci", "priority",
    }

    def __init__(self, medicaments: List[Medicament]):
        if not medicaments:
            raise ValueError("Catalogue vide : aucun medicament charge.")
        self._meds = medicaments
        self._par_amm: Dict[str, Medicament] = {m.num_amm: m for m in medicaments}
        self._par_nom: Dict[str, List[Medicament]] = {}
        for m in medicaments:
            self._par_nom.setdefault(normaliser(m.nom_specialite), []).append(m)

    # --- Chargement ---------------------------------------------------------

    @classmethod
    def charger(
        cls,
        chemin_csv: Path | str,
        regles: ReglesChaineFroid,
        *,
        rescale: bool = False,
        borne_min: float = 0.05,
        borne_max: float = 1.0,
    ) -> "Catalogue":
        """Lit le CSV et construit le catalogue.

        Args:
            chemin_csv: chemin vers pharmacy_delivery_priorities.csv.
            regles: regles de chaine du froid.
            rescale: si True, etire les priorites sur [borne_min, borne_max].
                     Si False (defaut), on garde les valeurs brutes du CSV.

        Raises:
            ValueError: colonnes manquantes ou priorites illisibles.
        """
        chemin_csv = Path(chemin_csv)
        # utf-8-sig : le CSV porte un BOM sur la premiere colonne.
        with open(chemin_csv, "r", encoding="utf-8-sig", newline="") as f:
            lecteur = csv.DictReader(f)
            entetes = set(lecteur.fieldnames or [])
            manquantes = cls.COLONNES_REQUISES - entetes
            if manquantes:
                raise ValueError(
                    f"{chemin_csv.name} : colonnes manquantes {sorted(manquantes)}. "
                    f"Trouvees : {sorted(entetes)}"
                )
            lignes = list(lecteur)

        brutes: List[float] = []
        for n, ligne in enumerate(lignes, start=2):
            try:
                brutes.append(float(ligne["priority"]))
            except (TypeError, ValueError) as exc:
                raise ValueError(
                    f"{chemin_csv.name} ligne {n} : priorite illisible "
                    f"({ligne.get('priority')!r})"
                ) from exc

        if rescale:
            pmin, pmax = min(brutes), max(brutes)
            etendue = pmax - pmin
            if etendue <= 0:
                brutes = [borne_max] * len(brutes)
            else:
                brutes = [
                    borne_min + (p - pmin) / etendue * (borne_max - borne_min)
                    for p in brutes
                ]

        meds: List[Medicament] = []
        for ligne, prio in zip(lignes, brutes):
            meds.append(
                Medicament(
                    num_amm=str(ligne["num_amm"]).strip(),
                    nom_specialite=str(ligne["nom_specialite"]).strip(),
                    dosage=str(ligne["dosage"]).strip(),
                    forme=str(ligne["forme"]).strip(),
                    dci=str(ligne["dci"]).strip(),
                    priorite=prio,
                    chaine_du_froid=regles.evaluer(
                        ligne["dci"], ligne["forme"], ligne["nom_specialite"]
                    ),
                )
            )
        return cls(meds)

    # --- Acces --------------------------------------------------------------

    def __len__(self) -> int:
        return len(self._meds)

    def __iter__(self):
        return iter(self._meds)

    @property
    def medicaments(self) -> List[Medicament]:
        return list(self._meds)

    def par_amm(self, num_amm: str) -> Medicament:
        """Recherche exacte par numero AMM.

        Raises:
            KeyError: si le numero est inconnu du referentiel.
        """
        cle = str(num_amm).strip()
        if cle not in self._par_amm:
            raise KeyError(f"num_amm inconnu du catalogue : {num_amm!r}")
        return self._par_amm[cle]

    def par_nom(self, nom: str) -> List[Medicament]:
        """Toutes les presentations portant ce nom de specialite."""
        return list(self._par_nom.get(normaliser(nom), []))

    def chercher(self, fragment: str, limite: int = 20) -> List[Medicament]:
        """Recherche approximative sur le nom, la DCI ou la forme."""
        f = normaliser(fragment)
        if not f:
            return []
        trouves = [
            m for m in self._meds
            if f in normaliser(m.nom_specialite)
            or f in normaliser(m.dci)
            or f in normaliser(m.forme)
        ]
        trouves.sort(key=lambda m: -m.priorite)
        return trouves[:limite]

    # --- Diagnostic ---------------------------------------------------------

    def resume(self) -> Dict[str, float]:
        """Statistiques de controle a afficher au demarrage."""
        prios = [m.priorite for m in self._meds]
        froids = sum(m.chaine_du_froid for m in self._meds)
        return {
            "nb_medicaments": len(self._meds),
            "nb_chaine_froid": froids,
            "part_chaine_froid": froids / len(self._meds),
            "priorite_min": min(prios),
            "priorite_max": max(prios),
            "priorite_moyenne": sum(prios) / len(prios),
            "nb_priorites_distinctes": len(set(prios)),
        }
