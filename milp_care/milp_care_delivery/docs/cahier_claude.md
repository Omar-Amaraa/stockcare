StockCare
| Predictive | Demand        |     | &          | Priority-Aware |     | Distribution |     |     |
| ---------- | ------------- | --- | ---------- | -------------- | --- | ------------ | --- | --- |
| Network    | for Tunisia’s |     | Pharmacies |                |     |              |     |     |
A closed-loop AI pipeline linking pharmacy shortage forecasting,
| national medicine |         | prioritization, |        | and      | optimized  | last-mile | delivery           |             |
| ----------------- | ------- | --------------- | ------ | -------- | ---------- | --------- | ------------------ | ----------- |
|                   |         |                 |        | Phase    |            | 1 —       | The Idea           |             |
|                   |         |                 |        |          | Submitted: | July      | 17, 2026           |             |
|                   |         |                 |        |          | Team       | Chaneb+   |                    |             |
|                   |         |                 | Adam   | Sabri    |            | HLAOUA    | — IMT Atlantique   |             |
|                   | Mohamed |                 | Mounib | HALOUANI |            | — Faculté | de Pharmacie       | de Monastir |
|                   |         |                 |        | Omar     | AMARA      | —         | IMT Atlantique     |             |
|                   |         |                 |        | Mohamed  |            | Aziz NCIR | — ENIT             |             |
|                   |         |                 | Malek  | Taieb    | NABLI      | —         | Ponts et Chaussées |             |

Contents
| 1 Executive | Summary |     | 2   |
| ----------- | ------- | --- | --- |
| 2 The       | Problem |     | 2   |
2.1 A chronic, structural shortage crisis . . . . . . . . . . . . . . . . . . . . . . . . . . . 2
2.2 Why the system keeps failing . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 2
| 3 Proposed | Solution | — System Overview | 3   |
| ---------- | -------- | ----------------- | --- |
3.1 Layer 1 — Pharmacy-Side Demand Forecasting Engine . . . . . . . . . . . . . . . 3
3.2 Layer 2 — Depot-Side Priority Classification Engine . . . . . . . . . . . . . . . . . 4
3.3 Layer 3 — MILP-Based Delivery Routing Engine . . . . . . . . . . . . . . . . . . . 5
| 4 Innovation  | & Technical | Novelty | 8   |
| ------------- | ----------- | ------- | --- |
| 5 Originality |             |         | 8   |
| 6 Quantified  | Impact      |         | 9   |
| 7 Feasibility |             |         | 9   |
7.1 Data sources required . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 9
7.2 Technical feasibility . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 10
7.3 Technology stack . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 10
7.4 Phased rollout . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 11
| 8 Target      | Audience        |     | 11  |
| ------------- | --------------- | --- | --- |
| 9 Competitive | Differentiation |     | 11  |
| 10 Business   | Model           |     | 12  |
| Sources       |                 |     | 12  |

| StockCare—Phase1: |     |         | TheIdea |     |     |     | July17,2026 |
| ----------------- | --- | ------- | ------- | --- | --- | --- | ----------- |
| 1 Executive       |     | Summary |         |     |     |     |             |
Tunisia’s pharmaceutical supply chain is reactive by design: pharmacies restock based on habit
and gut feeling, depots allocate scarce medicine on a first-come-first-served basis, and delivery
trucks follow routes drawn from experience rather than data. The result, documented exten-
sively in the national press over the last three years, is chronic, recurring stockouts of essential
| medicines | even | when | stock exists | somewhere | in the system | [1,2,3]. |     |
| --------- | ---- | ---- | ------------ | --------- | ------------- | -------- | --- |
StockCare closes this loop with three connected AI/OR components:
1. A pharmacy-side forecasting engine that predicts short-term medicine demand from con-
textual signals (region, season, calendar, exam periods, heatwaves) and compares it to real
| stock | to auto-generate |     | restock | requests. |     |     |     |
| ----- | ---------------- | --- | ------- | --------- | --- | --- | --- |
2. Adepot-sidepriorityenginethatscoreseverymedicineinthenationalcataloguebyclinical
and logistical criticality, so scarce supply and delivery capacity are allocated to what matters
| most | first. |     |     |     |     |     |     |
| ---- | ------ | --- | --- | --- | --- | --- | --- |
3. AMILP-basedroutingenginethatsequencesdeliveriestominimizedistance,fuel,andCO ,
2
while explicitly rewarding faster delivery of high-priority, cold-chain, or life-critical orders.
Nothing like this closed loop – forecasting → prioritization → priority-aware routing – currently
exists in Tunisia’s distribution chain. This document lays out the problem, the system design, the
underlying models and math, the projected impact, and how the team is splitting the work.
| 2 The | Problem  |            |          |        |     |     |     |
| ----- | -------- | ---------- | -------- | ------ | --- | --- | --- |
| 2.1 A | chronic, | structural | shortage | crisis |     |     |     |
Medicine shortages in Tunisia are not an occasional inconvenience – they are a standing feature
of the health system. Reporting places the number of unavailable products at times above 500
references across hospitals and private pharmacies [1], and pharmacists’ union representatives
have repeatedly cited a rolling shortage of 250 to 300 medicines at any given moment [2]. As
recently as January 2026, pharmacists were still flagging stock that had not been replenished
for over two months and escalating the issue to the national medicines agency [3]. A 2026 sec-
tor analysis (BMI/Fitch Solutions) warns that the combination of Pharmacie Centrale de Tunisie
(PCT) debt, dinar depreciation, and foreign-currency restrictions on pharmaceutical imports is
structurally pushing medicines toward being simultaneously scarcer and more expensive [4,5].
| 2.2 Why | the | system | keeps | failing |     |     |     |
| ------- | --- | ------ | ----- | ------- | --- | --- | --- |
The financial and macroeconomic causes (PCT debt, CNAM reimbursement delays, forex short-
ages) are real, but they are compounded – and made far worse in their day-to-day effect on
patients – by an information and logistics problem that is entirely solvable with data:
• Nodemandanticipation. Pharmaciesorderreactively,onceshelvesarealreadyempty. Pre-
dictable demand spikes – flu season, summer heatwaves, university exam periods driving
demand for specific OTC and chronic-treatment categories – are not modeled at all.
• No visibility for the depot. When dozens of pharmacies request the same scarce medicine
simultaneously, the depot has no systematic way to decide who should receive the limited
| stock | first | based | on clinical | urgency. |     |     |     |
| ----- | ----- | ----- | ----------- | -------- | --- | --- | --- |
• Nooptimizeddelivery. Onceanorderisapproved,thetripfromdepottopharmacyisplanned
| PredictiveMedicineDistributionNetwork |     |     |     |     | 2   |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- | --- |

| StockCare—Phase1: |     |     | TheIdea |     |     |     |     | July17,2026 |
| ----------------- | --- | --- | ------- | --- | --- | --- | --- | ----------- |
manually. A life-critical, cold-chain-sensitive order can sit behind lower-urgency stops purely
becauseofgeographicconvenience,wastingthetime,fuel,andCO budgetthatscarcedeliv-
2
| eries | cannot | afford | to lose. |     |     |     |     |     |
| ----- | ------ | ------ | -------- | --- | --- | --- | --- | --- |
Key insight
The shortage crisis has a financial root cause that a student project cannot fix. But a large
share of its patient-facing damage – the wrong medicine being out of stock at the wrong
pharmacy at the wrong time – is a forecasting, prioritization, and routing problem. That is
| exactly    | what | StockCare |     | targets. |     |          |     |     |
| ---------- | ---- | --------- | --- | -------- | --- | -------- | --- | --- |
| 3 Proposed |      | Solution  |     | — System |     | Overview |     |     |
StockCare is a three-layer closed loop connecting every pharmacy’s stock and point-of-sale sys-
| tem to | the depot’s | procurement |     | and | delivery | fleet. |     |     |
| ------ | ----------- | ----------- | --- | --- | -------- | ------ | --- | --- |
everypharmacy
|     |          |          |     | Demand   |         | Fore- |                |               |
| --- | -------- | -------- | --- | -------- | ------- | ----- | -------------- | ------------- |
|     | Pharmacy |          | IS  |          |         |       | Predicted      | Restock Query |
|     |          |          |     | casting  | Engine  |       |                |               |
|     | stock    | & sales  |     |          |         |       | need vs. stock | to depot      |
|     |          |          |     |          | (Layer  | 1)    |                |               |
|     |          | National |     |          |         |       | Depot Ag-      | Procurement   |
|     |          |          |     | Priority | Classi- |       |                |               |
|     | Medicine | DB       |     |          |         |       | gregator       | Orders        |
|     |          |          |     | fication | Engine  |       |                |               |
|     | Tunisia  | cat-     |     |          |         |       | all pharmacy   | to labs /     |
|     |          |          |     |          | (Layer  | 2)    |                |               |
|     |          | alogue   |     |          |         |       | queries        | factories     |
distance, fuel,
MILP Rout-
cold-chain,
ing Engine
priority co-
(Layer 3)
efficient
feedsbacknextstockread
Optimized
Delivery Routes
Figure 1: StockCare end-to-end pipeline: pharmacy forecasting → depot prioritization → priority-
| aware     | routing, | closing           | the loop | back | to pharmacy | stock.      |        |     |
| --------- | -------- | ----------------- | -------- | ---- | ----------- | ----------- | ------ | --- |
| 3.1 Layer |          | 1 — Pharmacy-Side |          |      | Demand      | Forecasting | Engine |     |
Forevery(pharmacy,medicine)pair,themodelforecastsexpecteddemandoverarollinghorizon
H (e.g. the next two weeks) and compares it to live stock pulled from the pharmacy’s information
system.
Modeling approach: a gradient-boosted tree ensemble (e.g. LightGBM/XGBoost) over engi-
neered lag and calendar features, or a hybrid statistical + ML model (seasonal decomposi-
tion combined with a residual learner), is a good fit given tabular, mixed categorical/numerical,
medium-frequency data. The output is a per-medicine demand forecast D for pharmacy i,
i,m
| PredictiveMedicineDistributionNetwork |     |     |     |     |     | 3   |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- |

| StockCare—Phase1: |       | TheIdea  |     |     |     |            |     |     | July17,2026 |     |
| ----------------- | ----- | -------- | --- | --- | --- | ---------- | --- | --- | ----------- | --- |
| Feature           | group | Examples |     |     | Why | it matters |     |     |             |     |
Geography Region / governorate Disease prevalence, climate, and demo-
|     |     |     |     |     | graphics | vary sharply | across | Tunisia |     |     |
| --- | --- | --- | --- | --- | -------- | ------------ | ------ | ------- | --- | --- |
Calendar Season, month, day Captures weekly and seasonal consumption
|     |     | of  | week, hour |     | cycles | (respiratory | illness | in winter, | etc.) |     |
| --- | --- | --- | ---------- | --- | ------ | ------------ | ------- | ---------- | ----- | --- |
Academic context Is-exam-period flag Spikes in demand for stimulants, anxiolytics,
|     |     |     |     |     | painkillers, | energy/vitamin |     | products |     |     |
| --- | --- | --- | --- | --- | ------------ | -------------- | --- | -------- | --- | --- |
Climate context Heatwave / weather- Spikes in rehydration salts, antihistamines,
|     |     | alert | flag |     | sunburn | and GI | treatments |     |     |     |
| --- | --- | ----- | ---- | --- | ------- | ------ | ---------- | --- | --- | --- |
Epidemiologicalsig- Flu-season index, Anticipates surges tied to seasonal epi-
| nal |     | outbreak | alerts |     | demics |     |     |     |     |     |
| --- | --- | -------- | ------ | --- | ------ | --- | --- | --- | --- | --- |
History Lagged sales Core autoregressive signal for any demand
|     |     | (7/14/30   |     | days),  | model |     |     |     |     |     |
| --- | --- | ---------- | --- | ------- | ----- | --- | --- | --- | --- | --- |
|     |     | stock-outs |     | history |       |     |     |     |     |     |
Live state Current stock level, Needed to compute the actual shortage gap,
|     |     | pending | orders |     | not just | raw demand |     |     |     |     |
| --- | --- | ------- | ------ | --- | -------- | ---------- | --- | --- | --- | --- |
Table 1: Feature set for the pharmacy-level demand forecasting model.
| medicine     | m, over horizon | H.  |     |         |      |         |         |     |     |     |
| ------------ | --------------- | --- | --- | ------- | ---- | ------- | ------- | --- | --- | --- |
| Shortage-gap | logic:          |     |     |         |      |         |         |     |     |     |
|              |                 |     |     | (cid:0) |      |         | (cid:1) |     |     |     |
|              |                 |     | G = | max 0,  | D +S | −X      |         |     |     | (1) |
|              |                 |     | i,m |         | i,m  | i,m i,m |         |     |     |     |
where S i,m is a safety-stock buffer and X i,m is the current stock read from the pharmacy’s in-
formation system. Whenever G exceeds a configurable threshold τ, the engine automatically
i,m
| emits a | restock query |     |     |     |     |     |     |     |     |     |
| ------- | ------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
Q = {pharmacy_id = i, medicine_id = m, quantity = G , urgency, timestamp}
| i,m |     |     |     |     |     |     | i,m |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
sent to the depot in real time – no manual phone calls or paper order forms.
| 3.2 Layer | 2 — Depot-Side |     | Priority | Classification |     | Engine |     |     |     |     |
| --------- | -------------- | --- | -------- | -------------- | --- | ------ | --- | --- | --- | --- |
The depot receives restock queries from many pharmacies simultaneously. Before anything can
berouted,thesystemneedstoknowwhichmedicine,forwhichpharmacy,mattersmostrightnow
–thisistheroleofthepriorityengine,whichdrawsonthenationalAMMregistrymaintainedbythe
DirectiondelaPharmacieetduMédicament(DPM),theMinistryofHealthbodyregisteringevery
medicine legally sold in Tunisia: at extraction time it held 6,058 active AMMs (4,280 génériques,
1,695 princeps, 67 biosimilaires) [8] – this is the scale of catalogue Layer 2 needs to ingest and
classify.
| Conceptually, | each | medicine | request | is scored | as: |     |     |     |     |     |
| ------------- | ---- | -------- | ------- | --------- | --- | --- | --- | --- | --- | --- |
P i,m = α·Criticality(m) + β ·StockoutRisk(i,m) + γ ·Irreplaceability(m)
|     |     |     | + δ·ColdChainSensitivity(m) |     |     | +   | ε·PopulationImpact(i,m) |     |     | (2) |
| --- | --- | --- | --------------------------- | --- | --- | --- | ----------------------- | --- | --- | --- |
with weights normalized (α+β +γ +δ+ε = 1) and P i,m ∈ [0,1], where:
• Criticality – is the medicine used for a chronic or life-threatening condition (e.g. insulin, car-
| diovascular,                          | oncology) | vs. | comfort/OTC | use; |     |     |     |     |     |     |
| ------------------------------------- | --------- | --- | ----------- | ---- | --- | --- | --- | --- | --- | --- |
| PredictiveMedicineDistributionNetwork |           |     |             |      | 4   |     |     |     |     |     |

| StockCare—Phase1: |     | TheIdea |     |     |     | July17,2026 |
| ----------------- | --- | ------- | --- | --- | --- | ----------- |
• StockoutRisk – how close pharmacy i already is to running out, and how long it has been
short;
• Irreplaceability – whether an equivalent generic/substitute is available nearby;
• ColdChainSensitivity – whether the medicine requires refrigerated transport, making delay
| itself | a risk of spoilage; |     |     |     |     |     |
| ------ | ------------------- | --- | --- | --- | --- | --- |
• PopulationImpact–howmanypatientsatthatpharmacydependonit(e.g. chronic-treatment
renewals).
This score feeds two downstream decisions: (a) how the depot orders from labs and factories
(higher-priority medicines are procured first and in larger safety margins), and (b) the routing
| engine described | next.    |                    |     |                 |     |     |
| ---------------- | -------- | ------------------ | --- | --------------- | --- | --- |
| The engine       | is split | into two connected |     | sub-components: |     |     |
Static scoring – a learned reference table from the AMM registry. The registry’s fields map
almost directly onto the static components of the score: substitute counts per DCI + dosage
yieldIrreplaceability (aprincepswithnoregisteredgenericscoreshigh),thegalenicform(Forme
/ Présentation) flags likely cold-chain requirements, and the DCI is the join key for an ATC
therapeutic-classenrichmentseparatingchronic/vitalfromcomfortuse. Asampleoftheenriched
registry is annotated by pharmacists with reference criticality scores, and a gradient-boosted re-
gressorgeneralizestheseannotationstothefullcatalogue. Becausethesourceispublic,official,
andregulator-maintained,theresultingscoringtableisreproducibleandauditablebyathirdparty
– a benchmark other solutions would need to match, rather than a black-box heuristic. Stock-
outRisk and PopulationImpact come from each pharmacy’s transactional history (Layer 1), not
| from the | registry. |     |     |     |     |     |
| -------- | --------- | --- | --- | --- | --- | --- |
Adaptive weighting – reinforcement-learning-based regulation. The weights (α,β,γ,δ,ε)
should not stay fixed: a seasonal epidemic should push up Criticality and ColdChainSensitiv-
ityforvaccines;animportdisruptionatonelaboratoryshouldpushupIrreplaceabilityforitsDCIs.
An RL agent regulates the weights over time from aggregated context (stockout rates by cat-
egory, cold-chain alerts, seasonal indicators, import-delay signals), rewarded on post-decision
KPIs (chronic-patient stockouts avoided, cold-chain waste avoided, critical-order delay) and pe-
nalizedforinducedoverstocking. Becauseexposinganuntrainedagenttoliveshortagedecisions
is unacceptable in healthcare, it is trained offline first on a digital twin of the pharmacy network,
then deployed with bounded step sizes, human validation above a threshold, and automatic fall-
back to the static weights if the reward signal becomes anomalous. Crucially, only the weights
become time-varying – the score’s form and the pr ∈ [0,1] interface exposed to Layer 3 stay
i
| identical, | keeping the | swap transparent |     | to the MILP. |     |     |
| ---------- | ----------- | ---------------- | --- | ------------ | --- | --- |
| Current    | status      |                  |     |              |     |     |
Both sub-components are scheduled for the next development iteration. Until they are deliv-
ered, Layer 3 (the MILP) treats the priority coefficient as a fixed constant (e.g. a 3-tier static
weight: chronic/vital, essential, comfort) so that routing development can proceed in parallel
and simply swap in the dynamic score once it is ready – the interface (a per-order priority
| value | pr ∈ [0,1]) | is designed | to make | that swap | trivial. |     |
| ----- | ----------- | ----------- | ------- | --------- | -------- | --- |
i
| 3.3 Layer | 3 — MILP-Based |     | Delivery | Routing | Engine |     |
| --------- | -------------- | --- | -------- | ------- | ------ | --- |
Once orders are prioritized, the depot must decide the order in which its heterogeneous fleet
visits pharmacies. This is a variant of the Vehicle Routing Problem (VRP), formulated as a
| PredictiveMedicineDistributionNetwork |     |     |     | 5   |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- |

StockCare—Phase1: TheIdea July17,2026
Mixed-Integer Linear Program (MILP) so that priority, distance, fuel, SLA deadlines, and cold-
chainconstraintsareoptimizedjointlyratherthanhandledbyad-hocrules. Theformulationbelow
is the consolidated Phase-1 model.
Sets
• N = {1,...,n}: pharmacies to be served; node 0: the depot.
• K = {1,...,k}: the heterogeneous fleet of delivery vehicles.
Temporal & geographic parameters
• d ∈ R+: distancebetweennodesi,j (km); t ∈ R+: directtraveltimebetweeni,j (hours).
ij ij
• σ ∈ R+: unloading time required at pharmacy i (hours).
i
• H ∈ R+: end of the planning horizon (e.g. 12h).
max
• T ∈ R+: target SLA delivery deadline for pharmacy i.
i
Vehicle & order parameters
• Q ∈ R+: maximum capacity of vehicle k; R ∈ {0,1}: 1 if vehicle k is refrigerated.
k k
• ck ∈ R+: usage cost (fuel/emissions) per km of vehicle k – now vehicle-specific to reflect a
f
heterogeneous fleet.
• q ∈ R+: total load required for pharmacy i’s order.
i
• cc ∈ {0,1}: 1 if order i requires the cold chain.
i
• pr ∈ [0,1]: normalized priority score of order i – fixed constant for now, later supplied
i
dynamically by Layer 2.
Objective weights & constants
• w ∈ R+: penalty weight for SLA lateness (dinars/hour).
p
• γ ∈ R+: a very small tie-break coefficient for latency.
• w ∈ R+: flat penalty for mobilizing an additional vehicle.
v
• M ∈ R+: an adjusted Big-M constant, defined as M = H + σ + t – tighter than a
ij ij max i ij
generic constant, which helps the solver.
Decision variables
• x ∈ {0,1}: 1 if vehicle k travels directly from node i to node j.
ijk
• z ∈ {0,1}: 1 if vehicle k is mobilized (leaves the depot).
k
• s ∈ R+: exact arrival time at node i.
i
• L ∈ R+: cumulative lateness at node i relative to T .
i i
Objective
(cid:88)(cid:88) (cid:88) (cid:88) (cid:88)
min ckd x + w ·pr ·L + γ pr ·s + w z (3)
f ij ijk p i i i i v k
k∈K i,j i∈N i∈N k∈K
(cid:124) (cid:123)(cid:122) (cid:125) (cid:124) (cid:123)(cid:122) (cid:125) (cid:124) (cid:123)(cid:122) (cid:125) (cid:124) (cid:123)(cid:122) (cid:125)
fuel/CO2 SLAlateness tie-break fleetsize
PredictiveMedicineDistributionNetwork 6

| StockCare—Phase1: |     |     | TheIdea |     |     |     |     |     |     | July17,2026 |     |
| ----------------- | --- | --- | ------- | --- | --- | --- | --- | --- | --- | ----------- | --- |
• Fuel / CO – cost of fuel and emissions across every leg actually driven.
2
• SLAlateness–thetimeoverrunbeyondeachorder’stargetdeadline,weightedbythatorder’s
priority: beinglateonahigh-priorityordercostsfarmorethanbeinglateonalow-priorityone.
• Tie-break – a deliberately tiny coefficient γ on arrival time itself, so the objective doesn’t go
“flat”oncealllatenesstermshitzero;itnudgesthesolvertostilldeliverurgentordersasearly
| as  | possible, | not | just on | time. |     |     |     |     |     |     |     |
| --- | --------- | --- | ------- | ----- | --- | --- | --- | --- | --- | --- | --- |
• Fleet size – a flat cost per vehicle mobilized, keeping the number of trucks and drivers used
to a minimum.
Every term is expressed in dinars, so the weights w ,γ,w stay interpretable and negotiable with
|     |     |     |     |     |     |     | p v |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
the depot’s operations team rather than being abstract tuning knobs.
Constraints
(cid:88)(cid:88)
|     |     | x   | = 1 |     |     | ∀i ∈ | N   | (single | service) |     | (4) |
| --- | --- | --- | --- | --- | --- | ---- | --- | ------- | -------- | --- | --- |
ijk
j
k∈K
|     | (cid:88) |         | (cid:88) |     |     |       |     |       |               |     |     |
| --- | -------- | ------- | -------- | --- | --- | ----- | --- | ----- | ------------- | --- | --- |
|     |          | x jik = | x ijk    |     |     | ∀i,∀k |     | (flow | conservation) |     | (5) |
|     | j        |         | j        |     |     |       |     |       |               |     |     |
(cid:88)
|     |     | x = | z   |     |     | ∀k  |     | (fleet | activation) |     | (6) |
| --- | --- | --- | --- | --- | --- | --- | --- | ------ | ----------- | --- | --- |
|     |     | 0jk | k   |     |     |     |     |        |             |     |     |
j∈N
|     | (cid:88) | (cid:16)(cid:88) | (cid:17) |      |     |     |     |            |     |     |     |
| --- | -------- | ---------------- | -------- | ---- | --- | --- | --- | ---------- | --- | --- | --- |
|     |          | q                | x ≤      | Q ·z |     | ∀k  |     | (capacity) |     |     | (7) |
|     |          | i                | ijk      | k k  |     |     |     |            |     |     |     |
|     | i∈N      | j                |          |      |     |     |     |            |     |     |     |
(cid:88)
|     |     | x jik ≤ | R k |     |     | ∀i : | cc i = 1, ∀k | (cold | chain) |     | (8) |
| --- | --- | ------- | --- | --- | --- | ---- | ------------ | ----- | ------ | --- | --- |
j
|     | s ≥ | s +σ   | +t −M | (1−x | )   | ∀i,j,k |     | (time            | & subtours) |     | (9)  |
| --- | --- | ------ | ----- | ---- | --- | ------ | --- | ---------------- | ----------- | --- | ---- |
|     | j   | i      | i ij  | ij   | ijk |        |     |                  |             |     |      |
|     | L   | ≥ s −T |       |      |     | ∀i     |     | (SLA             | overrun)    |     | (10) |
|     | i   | i      | i     |      |     |        |     |                  |             |     |      |
|     | L   | ≥ 0    |       |      |     | ∀i     |     | (non-negativity) |             |     | (11) |
i
|     | s = | 0   |     |     |     |     |     | (depot | clock) |     | (12) |
| --- | --- | --- | --- | --- | --- | --- | --- | ------ | ------ | --- | ---- |
0
| • Single |              | service | – every | pharmacy    | is visited | exactly | once.           |     |     |     |     |
| -------- | ------------ | ------- | ------- | ----------- | ---------- | ------- | --------------- | --- | --- | --- | --- |
| •        |              |         | –       | any vehicle | entering   | a node  | must also leave | it. |     |     |     |
| Flow     | conservation |         |         |             |            |         |                 |     |     |     |     |
• Fleet activation – z switches on as soon as vehicle k leaves the depot, linking it to the
k
| fleet-size |     | penalty | above. |     |     |     |     |     |     |     |     |
| ---------- | --- | ------- | ------ | --- | --- | --- | --- | --- | --- | --- | --- |
• Capacity–avehicle’stotalloadcanneverexceedQ ,andtyingtheboundtoz forcesz = 1
|     |        |            |     |       |     |     | k   |     |     | k   | k   |
| --- | ------ | ---------- | --- | ----- | --- | --- | --- | --- | --- | --- | --- |
| the | moment | it carries | any | load. |     |     |     |     |     |     |     |
• Cold chain – a pharmacy whose order needs the cold chain can only be reached by a refrig-
| erated | vehicle. |     |     |     |     |     |     |     |     |     |     |
| ------ | -------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
• Time & subtours – propagates arrival time leg by leg (including unloading time σ ) using a
i
tightened Big-M = H +σ +t ; because time strictly increases along any used arc, this
|     |     |     | ij  | max | i ij |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | ---- | --- | --- | --- | --- | --- | --- |
single family of constraints also rules out disconnected sub-loops that never pass through the
| depot | –   | no separate | MTZ | variables | needed. |     |     |     |     |     |     |
| ----- | --- | ----------- | --- | --------- | ------- | --- | --- | --- | --- | --- | --- |
• SLA overrun & non-negativity – lateness is the (possibly zero) overshoot past the target
| deadline |     | T ; arriving | early | never | produces | negative | “credit”. |     |     |     |     |
| -------- | --- | ------------ | ----- | ----- | -------- | -------- | --------- | --- | --- | --- | --- |
i
| PredictiveMedicineDistributionNetwork |     |     |     |     |     | 7   |     |     |     |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

StockCare—Phase1: TheIdea July17,2026
• Depot clock – the schedule starts at s = 0, anchoring every downstream arrival time.
0
In plain terms: the solver still wants short, fuel-efficient routes across a fleet of vehicles with
different costs, but every hour a high-priority order runs past its SLA is penalized far more than
thesamelatenessonalow-priorityorder–andevenwhennothingistechnicallylate,thetie-break
termstillnudgesurgentdeliveriestohappenfirst. Thatisexactlythetrade-offahumandispatcher
cannot reliably make at scale, especially once unloading time, cold-chain vehicle availability, and
a mixed fleet’s different per-km costs are all in play simultaneously.
Phase 1 scope, explicitly
For this phase, pr is a static, category-based constant (not yet produced by Layer 2). The
i
MILP is being built and validated against this constant so the optimization core, constraints,
and solver pipeline are proven before wiring in the dynamic priority score – avoiding a hard
dependency that would block progress.
4 Innovation & Technical Novelty
• Context-awaredemandforecasting,notgenerictime-seriesforecasting. Encodingexam
periods and heatwave/weather alerts as first-class model features is unusual even in com-
mercial forecasting tools, and directly tailored to observable Tunisian consumption patterns
(stimulant/analgesic demand during exams, rehydration/antihistamine demand during heat-
waves).
• Priority as an optimization coefficient, not a business rule. Most routing software opti-
mizes purely for cost and time. StockCare makes clinical/logistical urgency a first-class term
inside the MILP objective, so the routing decision itself reflects medical need, not just ge-
ography – and the priority score’s weights self-tune over time via reinforcement learning as
real-world outcomes come in.
• Agenuinelyclosedloop. Forecast→prioritize→route→deliver→updatestock→forecast
again. Each of the three layers is independently useful, but the compounding effect (accurate
demand signal feeding a well-prioritized, well-routed fleet) is where the real gain sits.
• Modular by design. Layer 2’s priority score is a pluggable interface (pr ∈ [0,1]); Layer 3
i
already runs on a constant placeholder, so the team can ship, test, and improve each layer
independently and swap in better models over time without re-architecting the system.
5 Originality
Individually, demand forecasting, medicine criticality scoring, and vehicle routing all exist else-
where as separate tools. What does not currently exist – in the Tunisian pharmaceutical dis-
tribution chain, or in the generic logistics tools available on the market – is the combination,
purpose-built for this exact context:
• Generic pharmacy ERPs/POS systems used by Tunisian officines track stock and sales; they
do not forecast contextual demand or talk to a depot’s routing system.
• Generic route-optimization software (fleet/logistics SaaS) optimizes cost and time; it has no
notion of medicine criticality or cold-chain urgency as a routing input.
• PCT/depot allocation today is manual and largely reactive to what is asked for, not to a data-
PredictiveMedicineDistributionNetwork 8

| StockCare—Phase1:   |     | TheIdea  |         |                       |     |          |     |     | July17,2026 |     |
| ------------------- | --- | -------- | ------- | --------------------- | --- | -------- | --- | --- | ----------- | --- |
| driven, system-wide |     | priority | ranking | across all pharmacies |     | at once. |     |     |             |     |
StockCare’s originality is in treating pharmacy, depot, and delivery fleet as one connected op-
timization problem instead of three separate manual processes – and in doing so specifically
for Tunisia’s distribution structure and its documented stress points (PCT debt, currency-driven
| import constraints, |     | chronic shortages) |     | [3,4,5]. |     |     |     |     |     |     |
| ------------------- | --- | ------------------ | --- | -------- | --- | --- | --- | --- | --- | --- |
| 6 Quantified        |     | Impact             |     |          |     |     |     |     |     |     |
Theseareprojectedtargetsforapilot,groundedinbenchmarksfromcomparableAI-forecasting
and route-optimization deployments elsewhere – not a guarantee, and not (yet) measured on
Tunisian data. Phase 2 validates them against a real pilot depot and its pharmacies.
| KPI |     |     | Projected | target | Basis |     |     |     |     |     |
| --- | --- | --- | --------- | ------ | ----- | --- | --- | --- | --- | --- |
Forecast error (vs. current −20% to −50% Published range for AI-driven de-
| manual reordering) |     |     |     |     | mand    | forecasting |     | vs. | traditional |     |
| ------------------ | --- | --- | --- | --- | ------- | ----------- | --- | --- | ----------- | --- |
|                    |     |     |     |     | methods |             | [6] |     |             |     |
Stockout incidents at pilot up to −65% IndustrybenchmarkforAIforecasting
| pharmacies |     |     |     |     | impact | on  | stockouts |     |     |     |
| ---------- | --- | --- | --- | --- | ------ | --- | --------- | --- | --- | --- |
[6]
Excess / expired inventory −20% to −30% Benchmark range for AI-guided in-
|     |     |     |     |     | ventory | optimization |     | [6] |     |     |
| --- | --- | --- | --- | --- | ------- | ------------ | --- | --- | --- | --- |
Delivery distance & fuel −15% to −25% TypicalrangereportedforVRP/route-
| use for depot | fleet |     |     |     | optimization |     | deployments |     | [7] |     |
| ------------- | ----- | --- | --- | --- | ------------ | --- | ----------- | --- | --- | --- |
CO emissions from deliv- −15% to −25% Directly proportional to fuel-use re-
2
| ery fleet |     |     |     |     | duction | above | [7] |     |     |     |
| --------- | --- | --- | --- | --- | ------- | ----- | --- | --- | --- | --- |
Delivery latency for high- internal target −30% Projecttarget–tobemeasuredinpi-
priority / cold-chain orders vs. unranked manual lot (no external benchmark; priority-
|     |     |     | dispatch |     | weighted |     | routing | is the | novel | ele- |
| --- | --- | --- | -------- | --- | -------- | --- | ------- | ------ | ----- | ---- |
ment)
Table 2: Projected Phase-1 impact targets, to be validated with real pilot data in Phase 2.
| Honesty check |     |     |     |     |     |     |     |     |     |     |
| ------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
NoneoftherowsaboveareTunisia-specificmeasurementsyet–theyareliterature-grounded
targetsthatjustifywhytheapproachisworthpiloting. Phase2shouldincludeinstrumentation
to measure every one of these KPIs against a live baseline before/after rollout, so Phase-1
| promises become |     | Phase-2 | evidence. |     |     |     |     |     |     |     |
| --------------- | --- | ------- | --------- | --- | --- | --- | --- | --- | --- | --- |
7 Feasibility
| 7.1 Data sources |     | required |     |     |     |     |     |     |     |     |
| ---------------- | --- | -------- | --- | --- | --- | --- | --- | --- | --- | --- |
• Pharmacy stock & sales – via API/export from each pharmacy’s information system (most
Tunisian officines already run some digital point-of-sale/stock software).
• Weather & heatwave alerts – Institut National de la Météorologie (INM) public data/API.
• Academic calendar – university/exam-period calendars (publicly published each year).
PredictiveMedicineDistributionNetwork 9

| StockCare—Phase1: |     | TheIdea |     |     |     |     | July17,2026 |     |
| ----------------- | --- | ------- | --- | --- | --- | --- | ----------- | --- |
• National medicine reference data – a catalogue of medicines sold in Tunisia, therapeu-
tic class, and essential-medicine status, cross-referenced against Ministry of Health / PCT
nomenclature. The DPM’s public AMM registry (AMMs par nom de spécialité) is a directly
usable starting point, holding 6,058 active AMMs at extraction time [8].
• Fleet&geography–depotvehiclecapacities,refrigerationavailability,andpharmacycoordi-
| nates for     | the routing | engine. |     |     |     |     |     |     |
| ------------- | ----------- | ------- | --- | --- | --- | --- | --- | --- |
| 7.2 Technical | feasibility |         |     |     |     |     |     |     |
Every component uses well-established, tractable techniques: gradient-boosted trees for tabular
demand forecasting, a multi-criteria weighted scoring model for priority, and MILP solved with
an off-the-shelf solver for routing. None of the three layers requires research-grade novelty to
implement a working version – the novelty is in how they are wired together.
| 7.3 Technology |     | stack |     |     |     |     |     |     |
| -------------- | --- | ----- | --- | --- | --- | --- | --- | --- |
Theplatformisbuiltasasetofcontainerizedservices,eachinthelanguagebestsuitedtoitsjob,
| communicating | over | REST | APIs:      |           |     |     |     |     |
| ------------- | ---- | ---- | ---------- | --------- | --- | --- | --- | --- |
| Component     |      |      | Technology | Rationale |     |     |     |     |
Backend / core services Java (Spring Boot) Robust, industry-standard stack for
|                   |           |     |           | the pharmacy–depot |                | business  |           | logic, |
| ----------------- | --------- | --- | --------- | ------------------ | -------------- | --------- | --------- | ------ |
|                   |           |     |           | order management,  |                | and       | API layer |        |
| Frontend          | (pharmacy |     | & Angular | Mature             | TypeScript     | framework |           | for    |
| depot dashboards) |           |     |           | the two web        | dashboards     |           | (pharmacy |        |
|                   |           |     |           | view and           | depot dispatch |           | view)     |        |
MILP routing engine Python + PuLP CleanalgebraicmodelingoftheVRP
formulation;solver-agnostic(CBCby
|     |     |     |     | default, swappable |                   | to a | commercial |     |
| --- | --- | --- | --- | ------------------ | ----------------- | ---- | ---------- | --- |
|     |     |     |     | solver for         | larger instances) |      |            |     |
Demand forecasting & Python (pandas, scikit- Standard ML tooling for tabular fore-
priority scoring learn / LightGBM) casting and the multi-criteria priority
model
Data storage RelationalDB(e.g. Post- Stock, orders, medicine catalogue,
|     |     |     | greSQL) | and delivery | history |     |     |     |
| --- | --- | --- | ------- | ------------ | ------- | --- | --- | --- |
Deployment&orchestra- Docker (docker- Each service ships as a container
| tion |     |     | compose) | – reproducible  | dev       | environments, |      |     |
| ---- | --- | --- | -------- | --------------- | --------- | ------------- | ---- | --- |
|      |     |     |          | straightforward | pilot     | deployment    |      | on  |
|      |     |     |          | a single        | host, and | a clean       | path | to  |
scale-out
Table 3: Planned technology stack: Java backend, Angular frontend, Python (PuLP) optimization
| core, Dockerized | services. |     |     |     |     |     |     |     |
| ---------------- | --------- | --- | --- | --- | --- | --- | --- | --- |
Thissplitkeepsadeliberateseambetweenthetransactional world(Javaserviceshandlingstock,
orders, and notifications) and the analytical world (Python services handling forecasting, prior-
ity scoring, and route optimization) – the two sides evolve independently and talk through well-
defined APIs, which is exactly what the modular, swappable design in Section 3 requires.
PredictiveMedicineDistributionNetwork 10

| StockCare—Phase1: |         | TheIdea |     |     |     | July17,2026 |
| ----------------- | ------- | ------- | --- | --- | --- | ----------- |
| 7.4 Phased        | rollout |         |     |     |     |             |
1. Pilot scope: one depot and a handful of partner pharmacies in a single region, a limited
medicine subset (e.g. top-selling chronic + high-shortage-risk medicines).
2. Static-priority MILP first (current phase): prove the routing core works end-to-end with a
| constant | priority | weight. |     |     |     |     |
| -------- | -------- | ------- | --- | --- | --- | --- |
3. Dynamicpriorityintegration: swapinthepriorityclassificationmodelonceready,re-validate
| routing | quality. |     |     |     |     |     |
| ------- | -------- | --- | --- | --- | --- | --- |
4. Scale-out: extend region coverage, medicine catalogue, and fleet size.
| 8 Target | Audience |     |     |     |     |     |
| -------- | -------- | --- | --- | --- | --- | --- |
• Primary: private pharmacies (officines) and hospital pharmacies across Tunisia, who gain
| automatic | restock | alerts instead | of reactive, | manual reordering. |     |     |
| --------- | ------- | -------------- | ------------ | ------------------ | --- | --- |
• Primary: regionaldepotsandwholesaledistributors(includingPCT-adjacentstructures),who
gain a prioritized, optimized view of what to procure and how to deliver it.
• Secondary / oversight: the Ministry of Health and medicine regulators, who benefit from
system-wide visibility into where shortages are emerging in near real time.
• Ultimate beneficiary: patients, particularly those on chronic treatment, who are the ones
| actually          | harmed         | by a stockout   | today.   |                   |                  |     |
| ----------------- | -------------- | --------------- | -------- | ----------------- | ---------------- | --- |
| 9 Competitive     |                | Differentiation |          |                   |                  |     |
| Capability        |                | Generic         | pharmacy | Generic           | logis- StockCare |     |
|                   |                | ERP/POS         |          | tics/VRP software |                  |     |
| Context-aware     | demand         | No              |          | No                | Yes              |     |
| forecasting       | (exam          | peri-           |          |                   |                  |     |
| ods, heatwaves,   | etc.)          |                 |          |                   |                  |     |
| Medicine-specific |                | prior- No       |          | No                | Yes              |     |
| ity/criticality   | scoring        |                 |          |                   |                  |     |
| Priority          | as a routing   | op- No          |          | Cost/time only    | Yes              |     |
| timization        | input          |                 |          |                   |                  |     |
| Automatic         | pharmacy       | → No            |          | N/A               | Yes              |     |
| depot restock     | query          |                 |          |                   |                  |     |
| Closed            | loop (forecast | No              |          | No                | Yes              |     |
| → prioritize      | → route        | →               |          |                   |                  |     |
feedback)
| Tailored  | to Tunisia’s | N/A |     | No  | Yes |     |
| --------- | ------------ | --- | --- | --- | --- | --- |
| PCT/depot | structure    |     |     |     |     |     |
Table 4: No single existing tool covers all three layers together for this specific market.
| PredictiveMedicineDistributionNetwork |     |     |     | 11  |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- |

| StockCare—Phase1: |     | TheIdea |     |     |     |     |     |     |     | July17,2026 |
| ----------------- | --- | ------- | --- | --- | --- | --- | --- | --- | --- | ----------- |
| 10 Business       |     | Model   |     |     |     |     |     |     |     |             |
Themodelisbuiltaroundasimpleprinciple: pharmaciesanddepotspaylessforStockCarethan
they currently lose without it, so the subscription is framed as protecting an existing loss rather
| than as | a new cost. |     |     |     |     |     |     |     |     |     |
| ------- | ----------- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
Value delivered. A pharmacy loses roughly 107TND/month in margin per chronic patient it
fails to retain (a 3-month refill of ∼800TND at a 40% margin). For a mid-size depot fleet, the
platform’s routing (a ∼20% distance reduction) plus reduced spoilage yields on the order of
1,250TND/month in fuel and cold-chain savings – visible in the first fuel bill, with no new trucks,
| stock, or | staff. |       |     |     |       |     |     |                 |     |         |
| --------- | ------ | ----- | --- | --- | ----- | --- | --- | --------------- | --- | ------- |
| Segment   |        | Price |     |     | Trial |     |     | Value protected |     | / saved |
Pharmacy 100TND/mo 1 month free, install in- ∼960TND/mo margin at
|     |     |     |     |     | cluded |     |     | risk from | stockouts |     |
| --- | --- | --- | --- | --- | ------ | --- | --- | --------- | --------- | --- |
Depot 1,100TND/mo 3 months free, install + ∼1,250TND/mo fuel +
|             |     |       |     |       | training |     |     | spoilage | savings      |        |
| ----------- | --- | ----- | --- | ----- | -------- | --- | --- | -------- | ------------ | ------ |
| Transaction |     | 0.5%  | of  | order | —        |     |     | Applied  | to orders    | routed |
|             |     | value |     |       |          |     |     | through  | the platform |        |
Table5: Pricinganchoredbelowthelosseachsegmentalreadyabsorbs;freetrialsremoveadop-
tion risk.
Unit economics & moat. With an assumed 30% trial-to-paid conversion, LTV:CAC is favorable
on both sides (roughly 16:1 for pharmacies, 20:1 for depots) with payback under ∼2 months.
The defensible asset is the proprietary, compounding dataset: the more pharmacies and depots
the system connects, the better its forecasts and priority scores become, and the harder it is to
replicate. Rollout follows the fleet’s geography – starting in Sousse (Year 1), expanding across
the Sahel, then Grand Tunis, Sfax, and the remaining governorates toward national coverage by
Year 5.
1. Lock down the static-priority MILP against a synthetic or small real pharmacy/depot dataset.
2. Design and start training the priority classification model against the national medicine refer-
| ence        | database.    |       |     |       |         |           |     |     |     |     |
| ----------- | ------------ | ----- | --- | ----- | ------- | --------- | --- | --- | --- | --- |
| 3. Finalize | the business | model | and | pilot | partner | outreach. |     |     |     |     |
4. Build the demand-forecasting model’s feature pipeline (weather API, academic calendar, his-
| torical | sales) and | connect | it to | a first | partner | pharmacy’s | stock | data. |     |     |
| ------- | ---------- | ------- | ----- | ------- | ------- | ---------- | ----- | ----- | --- | --- |
5. Integrate all three layers end-to-end on the pilot scope; instrument the KPIs listed in Section
| 6 to | replace projected |     | targets | with | measured | pilot results. |     |     |     |     |
| ---- | ----------------- | --- | ------- | ---- | -------- | -------------- | --- | --- | --- | --- |
Sources
[1] AlloDocteurs,“Lapénuriedemédicamentss’aggraveenTunisie”,2025. https://www.allodocteurs
.fr/la-penurie-de-medicaments-saggrave-en-tunisie-44225.html
[2] Anadolu Agency, “Tunisie: pénurie de 250 à 300 médicaments dans les pharmacies du pays”, 2023.
https://www.aa.com.tr/fr/afrique/tunisie-p%C3%A9nurie-de-250-%C3%A0-300-m%C3%A9dicame
nts-dans-les-pharmacies-du-pays/2874641
| PredictiveMedicineDistributionNetwork |     |     |     |     |     | 12  |     |     |     |     |
| ------------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

StockCare—Phase1: TheIdea July17,2026
[3] LaPressedeTunisie,“Pénuriedemédicaments: c’estunequestiondevieoudemort!”,January2026.
https://www.lapresse.tn/2026/01/14/penurie-de-medicaments-cest-une-question-de-vie-o
u-de-mort/
[4] Business News, “Pourquoi les médicaments seront plus chers et plus rares” (citing BMI/Fitch Solu-
tions), May 2026. https://businessnews.com.tn/2026/05/21/pourquoi-les-medicaments-seron
t-plus-chers-et-plus-rares/1402663/
[5] TunisieFocus,“LatragédiedupharmaceutiqueenTunisie”,May2026. https://www.tunisiefocus.c
om/economie/la-tragedie-du-pharmaceutique-en-tunisie-323508/
[6] IndustrysummariesofMcKinsey&CompanyresearchonAI-drivendemandforecasting(forecast-error
andstockout-reductionbenchmarks),viaToolsGroup,ITU/MDPIreview,andrelatedindustryanalyses,
2025–2026.
[7] Industry benchmarks on route-optimization / VRP fuel and distance savings, via GoBolt, Locus, and
Optiyollogisticsanalyses,2025–2026.
[8] Direction de la Pharmacie et du Médicament (DPM), Tunisia, “AMMs par nom de spécialité” (public
registryofMarketingAuthorizationsforhuman-usemedicines),consulted2026. https://dpm.tn/med
icaments-a-usage-humain/amms-par-nom-de-specialite
PredictiveMedicineDistributionNetwork 13