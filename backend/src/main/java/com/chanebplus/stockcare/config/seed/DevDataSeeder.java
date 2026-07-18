package com.chanebplus.stockcare.config.seed;

import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.depot.domain.DepotPharmacy;
import com.chanebplus.stockcare.modules.delivery.domain.Driver;
import com.chanebplus.stockcare.modules.delivery.domain.Vehicle;
import com.chanebplus.stockcare.modules.delivery.repo.DriverRepository;
import com.chanebplus.stockcare.modules.delivery.repo.VehicleRepository;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.depot.repo.DepotRepository;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.inventory.repo.InventoryItemRepository;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.medication.repo.MedicationRepository;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.pharmacy.repo.PharmacyRepository;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionResult;
import com.chanebplus.stockcare.modules.prediction.repo.PredictionResultRepository;
import com.chanebplus.stockcare.modules.prediction.spi.ShortagePrediction;
import com.chanebplus.stockcare.modules.prediction.spi.StockPredictionService;
import com.chanebplus.stockcare.modules.priority.service.PriorityService;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.modules.user.domain.User;
import com.chanebplus.stockcare.modules.user.domain.UserStatus;
import com.chanebplus.stockcare.modules.user.repo.UserRepository;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a rich development dataset (depot, pharmacies, users, catalogue, inventory, predictions and
 * sample requests) on first startup. Runs only when {@code stockcare.seed.enabled=true} and the
 * database has no users yet. Credentials are for the development profile only.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "stockcare.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEFAULT_PASSWORD = "Password123!";

    private final DepotRepository depotRepository;
    private final PharmacyRepository pharmacyRepository;
    private final DepotPharmacyRepository depotPharmacyRepository;
    private final UserRepository userRepository;
    private final MedicationRepository medicationRepository;
    private final InventoryItemRepository inventoryRepository;
    private final PredictionResultRepository predictionRepository;
    private final PharmacyRequestRepository requestRepository;
    private final PriorityService priorityService;
    private final StockPredictionService predictor;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationClock clock;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    public DevDataSeeder(DepotRepository depotRepository, PharmacyRepository pharmacyRepository,
                         DepotPharmacyRepository depotPharmacyRepository, UserRepository userRepository,
                         MedicationRepository medicationRepository, InventoryItemRepository inventoryRepository,
                         PredictionResultRepository predictionRepository,
                         PharmacyRequestRepository requestRepository, PriorityService priorityService,
                         StockPredictionService predictor, PasswordEncoder passwordEncoder,
                         ApplicationClock clock, VehicleRepository vehicleRepository,
                         DriverRepository driverRepository) {
        this.depotRepository = depotRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.depotPharmacyRepository = depotPharmacyRepository;
        this.userRepository = userRepository;
        this.medicationRepository = medicationRepository;
        this.inventoryRepository = inventoryRepository;
        this.predictionRepository = predictionRepository;
        this.requestRepository = requestRepository;
        this.priorityService = priorityService;
        this.predictor = predictor;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Seed skipped: database already populated.");
            return;
        }
        log.info("Seeding development data...");

        Depot depot = depot("DEP-TUN", "Depot Central Tunis", "Zone Industrielle", "Tunis", "Tunis",
                36.8000, 10.1800, "+216 71 000 000");

        Pharmacy tunis = pharmacy("PH-TUN", "Pharmacie Centrale Tunis", "PH-2025-001", "Av. Habib Bourguiba",
                "Tunis", "Tunis", 36.8065, 10.1815);
        Pharmacy ariana = pharmacy("PH-ARI", "Pharmacie El Menzah", "PH-2025-002", "Rue du Lac",
                "Ariana", "Ariana", 36.8625, 10.1956);
        Pharmacy sfax = pharmacy("PH-SFA", "Pharmacie Sfax Ville", "PH-2025-003", "Av. Hedi Chaker",
                "Sfax", "Sfax", 34.7406, 10.7603);
        Pharmacy sousse = pharmacy("PH-SOU", "Pharmacie Sousse Medina", "PH-2025-004", "Rue de la Kasbah",
                "Sousse", "Sousse", 35.8256, 10.6360);
        Pharmacy nabeul = pharmacy("PH-NAB", "Pharmacie Nabeul Centre", "PH-2025-005", "Av. Habib Thameur",
                "Nabeul", "Nabeul", 36.4513, 10.7357);
        List<Pharmacy> pharmacies = List.of(tunis, ariana, sfax, sousse, nabeul);
        pharmacies.forEach(p -> link(depot, p));

        user("admin@stockcare.tn", "StockCare Admin", Role.ADMIN, null, null);
        user("depot@stockcare.tn", "Depot Manager", Role.DEPOT, null, depot);
        user("ph.tunis@stockcare.tn", "Pharmacien Tunis", Role.PHARMACY, tunis, null);
        user("ph.ariana@stockcare.tn", "Pharmacien Ariana", Role.PHARMACY, ariana, null);
        user("ph.sfax@stockcare.tn", "Pharmacien Sfax", Role.PHARMACY, sfax, null);
        user("ph.sousse@stockcare.tn", "Pharmacien Sousse", Role.PHARMACY, sousse, null);
        user("ph.nabeul@stockcare.tn", "Pharmacien Nabeul", Role.PHARMACY, nabeul, null);

        Map<String, Medication> meds = seedMedications();

        // Inventory: give each pharmacy a mix; Tunis and Sfax get low-stock, high-consumption cases.
        String[] common = {"PARA500", "AMOX500", "OMEP20", "IBU400", "CETI10", "ORS"};
        for (Pharmacy p : pharmacies) {
            int i = 0;
            for (String sku : common) {
                inventory(p, meds.get(sku), 120 - i * 10, 40, 6 + i, null);
                i++;
            }
        }
        // Explicit shortage cases
        inventory(tunis, meds.get("INSGLA"), 12, 20, 4.0, null);     // insulin, cold chain, low
        inventory(tunis, meds.get("SALB100"), 8, 15, 3.5, null);     // salbutamol low
        inventory(sfax, meds.get("AMOX500"), 15, 60, 12.0, null);    // override common: high demand
        inventory(sousse, meds.get("METF850"), 30, 40, 9.0, null);
        inventory(ariana, meds.get("ENOX40"), 6, 15, 2.5, null);     // enoxaparin cold chain low

        // Fleet
        vehicle(depot, "VH-01", "TUN-1234", 300, false);
        vehicle(depot, "VH-02", "TUN-5678", 150, true);   // refrigerated (cold chain)
        driver(depot, "Karim Ben Ali", "+216 20 111 111");
        driver(depot, "Sonia Trabelsi", "+216 20 222 222");

        // Seed simulated predictions for Tunis so the dashboard and draft-from-prediction work.
        seedPredictions(tunis);

        // Sample requests
        PharmacyRequest sfaxReq = request(sfax, depot, Urgency.HIGH, RequestStatus.SUBMITTED,
                "Rupture amoxicilline, forte demande", 30,
                item(meds.get("AMOX500"), 300, "Restock urgent"));
        sfaxReq.setSubmittedAt(clock.now());
        requestRepository.save(sfaxReq);

        PharmacyRequest tunisReq = request(tunis, depot, Urgency.CRITICAL, RequestStatus.SUBMITTED,
                "Insuline patients chroniques", 45,
                item(meds.get("INSGLA"), 60, "Chaine du froid"));
        tunisReq.setSubmittedAt(clock.now());
        tunisReq = requestRepository.save(tunisReq);
        priorityService.calculateFor(tunisReq);
        tunisReq.setStatus(RequestStatus.PRIORITIZED);
        requestRepository.save(tunisReq);

        log.info("Seed complete: 1 depot, {} pharmacies, {} medications, sample inventory/requests.",
                pharmacies.size(), meds.size());
    }

    // ---------- builders ----------

    private Depot depot(String code, String name, String addr, String region, String city,
                        double lat, double lon, String phone) {
        Depot d = new Depot();
        d.setCode(code); d.setName(name); d.setAddressLine(addr); d.setRegion(region);
        d.setCity(city); d.setLatitude(lat); d.setLongitude(lon); d.setPhone(phone);
        return depotRepository.save(d);
    }

    private Pharmacy pharmacy(String code, String name, String lic, String addr, String region,
                              String city, double lat, double lon) {
        Pharmacy p = new Pharmacy();
        p.setCode(code); p.setName(name); p.setLicenseNumber(lic); p.setAddressLine(addr);
        p.setRegion(region); p.setCity(city); p.setLatitude(lat); p.setLongitude(lon);
        p.setPhone("+216 70 000 000");
        return pharmacyRepository.save(p);
    }

    private void vehicle(Depot depot, String code, String plate, int capacity, boolean refrigerated) {
        Vehicle v = new Vehicle();
        v.setDepot(depot); v.setCode(code); v.setPlateNumber(plate);
        v.setCapacityUnits(capacity); v.setRefrigerated(refrigerated); v.setActive(true);
        vehicleRepository.save(v);
    }

    private void driver(Depot depot, String name, String phone) {
        Driver d = new Driver();
        d.setDepot(depot); d.setFullName(name); d.setPhone(phone); d.setActive(true);
        driverRepository.save(d);
    }

    private void link(Depot depot, Pharmacy pharmacy) {
        DepotPharmacy dp = new DepotPharmacy();
        dp.setDepot(depot); dp.setPharmacy(pharmacy);
        depotPharmacyRepository.save(dp);
    }

    private void user(String email, String name, Role role, Pharmacy pharmacy, Depot depot) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setPharmacy(pharmacy);
        u.setDepot(depot);
        u.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        userRepository.save(u);
    }

    private Medication medication(String name, String generic, String dosage, String form, String pkg,
                                  String sku, String atc, String category, boolean coldChain, double crit) {
        Medication m = new Medication();
        m.setName(name); m.setGenericName(generic); m.setDosage(dosage); m.setPharmaceuticalForm(form);
        m.setPackageSize(pkg); m.setSku(sku); m.setAtcCode(atc); m.setCategory(category);
        m.setColdChain(coldChain); m.setCriticalityScore(crit);
        return medicationRepository.save(m);
    }

    private void inventory(Pharmacy p, Medication m, int current, int min, double adc, Integer reorder) {
        if (m == null || inventoryRepository.existsByPharmacyIdAndMedicationId(p.getId(), m.getId())) {
            return;
        }
        InventoryItem it = new InventoryItem();
        it.setPharmacy(p); it.setMedication(m);
        it.setCurrentQuantity(current); it.setMinimumQuantity(min);
        it.setAverageDailyConsumption(adc); it.setReorderThreshold(reorder);
        inventoryRepository.save(it);
    }

    private PharmacyRequestItem item(Medication m, int qty, String note) {
        PharmacyRequestItem it = new PharmacyRequestItem();
        it.setMedication(m); it.setRequestedQuantity(qty); it.setNote(note);
        return it;
    }

    private PharmacyRequest request(Pharmacy pharmacy, Depot depot, Urgency urgency, RequestStatus status,
                                    String notes, Integer patients, PharmacyRequestItem... items) {
        PharmacyRequest r = new PharmacyRequest();
        r.setPharmacy(pharmacy); r.setDepot(depot); r.setUrgency(urgency); r.setStatus(status);
        r.setNotes(notes); r.setAffectedPatients(patients);
        for (PharmacyRequestItem it : items) {
            r.addItem(it);
        }
        return r;
    }

    private void seedPredictions(Pharmacy pharmacy) {
        List<InventoryItem> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        Instant now = clock.now();
        List<ShortagePrediction> predictions = predictor.predictShortages(pharmacy, inventory, now);
        Map<java.util.UUID, Medication> byId = new HashMap<>();
        inventory.forEach(i -> byId.put(i.getMedication().getId(), i.getMedication()));
        List<PredictionResult> toSave = new ArrayList<>();
        for (ShortagePrediction pr : predictions) {
            PredictionResult e = new PredictionResult();
            e.setPharmacy(pharmacy);
            e.setMedication(byId.get(pr.medicationId()));
            e.setCurrentStock(pr.currentStock());
            e.setPredictedShortageDate(pr.predictedShortageDate());
            e.setEstimatedRemainingDays(pr.estimatedRemainingDays());
            e.setPredictedMissingQuantity(pr.predictedMissingQuantity());
            e.setConfidence(pr.confidence());
            e.setReason(pr.reason());
            e.setModelVersion(pr.modelVersion());
            e.setSimulated(pr.simulated());
            e.setPredictionTime(now);
            toSave.add(e);
        }
        predictionRepository.saveAll(toSave);
    }

    private Map<String, Medication> seedMedications() {
        Map<String, Medication> m = new HashMap<>();
        m.put("PARA500", medication("Paracetamol 500mg", "Paracetamol", "500mg", "TABLET", "Box of 20",
                "PARA500", "N02BE01", "Analgesic", false, 0.50));
        m.put("AMOX500", medication("Amoxicillin 500mg", "Amoxicillin", "500mg", "CAPSULE", "Box of 16",
                "AMOX500", "J01CA04", "Antibiotic", false, 0.80));
        m.put("INSGLA", medication("Insulin Glargine 100UI/ml", "Insulin glargine", "100UI/ml", "INJECTION",
                "Pen 3ml", "INSGLA", "A10AE04", "Antidiabetic", true, 0.95));
        m.put("SALB100", medication("Salbutamol 100mcg", "Salbutamol", "100mcg", "INHALER", "200 doses",
                "SALB100", "R03AC02", "Bronchodilator", false, 0.85));
        m.put("METF850", medication("Metformin 850mg", "Metformin", "850mg", "TABLET", "Box of 30",
                "METF850", "A10BA02", "Antidiabetic", false, 0.80));
        m.put("AMLO5", medication("Amlodipine 5mg", "Amlodipine", "5mg", "TABLET", "Box of 30",
                "AMLO5", "C08CA01", "Antihypertensive", false, 0.75));
        m.put("OMEP20", medication("Omeprazole 20mg", "Omeprazole", "20mg", "CAPSULE", "Box of 28",
                "OMEP20", "A02BC01", "PPI", false, 0.50));
        m.put("LEVO50", medication("Levothyroxine 50mcg", "Levothyroxine", "50mcg", "TABLET", "Box of 30",
                "LEVO50", "H03AA01", "Thyroid", false, 0.80));
        m.put("ATOR20", medication("Atorvastatin 20mg", "Atorvastatin", "20mg", "TABLET", "Box of 30",
                "ATOR20", "C10AA05", "Statin", false, 0.60));
        m.put("IBU400", medication("Ibuprofen 400mg", "Ibuprofen", "400mg", "TABLET", "Box of 20",
                "IBU400", "M01AE01", "NSAID", false, 0.40));
        m.put("CEFT1G", medication("Ceftriaxone 1g", "Ceftriaxone", "1g", "INJECTION", "Vial",
                "CEFT1G", "J01DD04", "Antibiotic", false, 0.85));
        m.put("WARF5", medication("Warfarin 5mg", "Warfarin", "5mg", "TABLET", "Box of 30",
                "WARF5", "B01AA03", "Anticoagulant", false, 0.90));
        m.put("PRED5", medication("Prednisolone 5mg", "Prednisolone", "5mg", "TABLET", "Box of 20",
                "PRED5", "H02AB06", "Corticosteroid", false, 0.60));
        m.put("ENOX40", medication("Enoxaparin 4000UI", "Enoxaparin", "4000UI", "INJECTION", "Syringe",
                "ENOX40", "B01AB05", "Anticoagulant", true, 0.90));
        m.put("HCTZ25", medication("Hydrochlorothiazide 25mg", "Hydrochlorothiazide", "25mg", "TABLET", "Box of 30",
                "HCTZ25", "C03AA03", "Diuretic", false, 0.60));
        m.put("CLOP75", medication("Clopidogrel 75mg", "Clopidogrel", "75mg", "TABLET", "Box of 30",
                "CLOP75", "B01AC04", "Antiplatelet", false, 0.70));
        m.put("FURO40", medication("Furosemide 40mg", "Furosemide", "40mg", "TABLET", "Box of 30",
                "FURO40", "C03CA01", "Diuretic", false, 0.70));
        m.put("AZIT250", medication("Azithromycin 250mg", "Azithromycin", "250mg", "TABLET", "Box of 6",
                "AZIT250", "J01FA10", "Antibiotic", false, 0.75));
        m.put("DIAZ10", medication("Diazepam 10mg", "Diazepam", "10mg", "TABLET", "Box of 20",
                "DIAZ10", "N05BA01", "Anxiolytic", false, 0.65));
        m.put("MORPH10", medication("Morphine 10mg/ml", "Morphine", "10mg/ml", "INJECTION", "Ampoule",
                "MORPH10", "N02AA01", "Opioid analgesic", false, 0.90));
        m.put("ORS", medication("Oral Rehydration Salts", "ORS", "-", "SACHET", "Box of 10",
                "ORS", "A07CA", "Rehydration", false, 0.70));
        m.put("CETI10", medication("Cetirizine 10mg", "Cetirizine", "10mg", "TABLET", "Box of 15",
                "CETI10", "R06AE07", "Antihistamine", false, 0.40));
        m.put("VACFLU", medication("Influenza Vaccine", "Influenza vaccine", "0.5ml", "INJECTION", "Syringe",
                "VACFLU", "J07BB", "Vaccine", true, 0.90));
        m.put("VACHEP", medication("Hepatitis B Vaccine", "Hepatitis B vaccine", "1ml", "INJECTION", "Vial",
                "VACHEP", "J07BC01", "Vaccine", true, 0.90));
        return m;
    }
}
