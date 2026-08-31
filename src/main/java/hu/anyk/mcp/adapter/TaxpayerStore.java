package hu.anyk.mcp.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class TaxpayerStore {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path storeFile;
    private Map<String, TaxpayerProfile> profiles;

    public TaxpayerStore(String anykRoot) {
        this.storeFile = Path.of(anykRoot, "taxpayers.json");
        load();
    }

    public record TaxpayerProfile(
        String id,
        String nev,
        String szuletesiNev,
        String adoazonosito,
        String adoszam,
        String tajSzam,
        String szuletesiDatum,
        String szuletesiHely,
        String anyjaNeve,
        String allampolgarsag,
        String iranyitoszam,
        String telepules,
        String kozteruletNev,
        String kozteruletTipus,
        String hazszam,
        String emelet,
        String ajto,
        String telefon,
        String email,
        String bankszamlaszam,
        String megjegyzes,
        Map<String, String> extra
    ) {
        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String id, nev, szuletesiNev, adoazonosito, adoszam, tajSzam;
            private String szuletesiDatum, szuletesiHely, anyjaNeve, allampolgarsag;
            private String iranyitoszam, telepules, kozteruletNev, kozteruletTipus;
            private String hazszam, emelet, ajto, telefon, email, bankszamlaszam;
            private String megjegyzes;
            private Map<String, String> extra = new LinkedHashMap<>();

            public Builder id(String v) { id = v; return this; }
            public Builder nev(String v) { nev = v; return this; }
            public Builder szuletesiNev(String v) { szuletesiNev = v; return this; }
            public Builder adoazonosito(String v) { adoazonosito = v; return this; }
            public Builder adoszam(String v) { adoszam = v; return this; }
            public Builder tajSzam(String v) { tajSzam = v; return this; }
            public Builder szuletesiDatum(String v) { szuletesiDatum = v; return this; }
            public Builder szuletesiHely(String v) { szuletesiHely = v; return this; }
            public Builder anyjaNeve(String v) { anyjaNeve = v; return this; }
            public Builder allampolgarsag(String v) { allampolgarsag = v; return this; }
            public Builder iranyitoszam(String v) { iranyitoszam = v; return this; }
            public Builder telepules(String v) { telepules = v; return this; }
            public Builder kozteruletNev(String v) { kozteruletNev = v; return this; }
            public Builder kozteruletTipus(String v) { kozteruletTipus = v; return this; }
            public Builder hazszam(String v) { hazszam = v; return this; }
            public Builder emelet(String v) { emelet = v; return this; }
            public Builder ajto(String v) { ajto = v; return this; }
            public Builder telefon(String v) { telefon = v; return this; }
            public Builder email(String v) { email = v; return this; }
            public Builder bankszamlaszam(String v) { bankszamlaszam = v; return this; }
            public Builder megjegyzes(String v) { megjegyzes = v; return this; }
            public Builder extra(Map<String, String> v) { extra = v; return this; }
            public Builder putExtra(String k, String v) { extra.put(k, v); return this; }

            public TaxpayerProfile build() {
                if (id == null || id.isEmpty()) id = UUID.randomUUID().toString().substring(0, 8);
                return new TaxpayerProfile(id, nev, szuletesiNev, adoazonosito, adoszam, tajSzam,
                    szuletesiDatum, szuletesiHely, anyjaNeve, allampolgarsag,
                    iranyitoszam, telepules, kozteruletNev, kozteruletTipus,
                    hazszam, emelet, ajto, telefon, email, bankszamlaszam,
                    megjegyzes, extra);
            }
        }

        public Map<String, String> toFieldMap() {
            Map<String, String> map = new LinkedHashMap<>();
            if (nev != null) map.put("nev", nev);
            if (szuletesiNev != null) map.put("szuletesiNev", szuletesiNev);
            if (adoazonosito != null) map.put("adoazonosito", adoazonosito);
            if (adoszam != null) map.put("adoszam", adoszam);
            if (tajSzam != null) map.put("tajSzam", tajSzam);
            if (szuletesiDatum != null) map.put("szuletesiDatum", szuletesiDatum);
            if (szuletesiHely != null) map.put("szuletesiHely", szuletesiHely);
            if (anyjaNeve != null) map.put("anyjaNeve", anyjaNeve);
            if (allampolgarsag != null) map.put("allampolgarsag", allampolgarsag);
            if (iranyitoszam != null) map.put("iranyitoszam", iranyitoszam);
            if (telepules != null) map.put("telepules", telepules);
            if (kozteruletNev != null) map.put("kozteruletNev", kozteruletNev);
            if (kozteruletTipus != null) map.put("kozteruletTipus", kozteruletTipus);
            if (hazszam != null) map.put("hazszam", hazszam);
            if (emelet != null) map.put("emelet", emelet);
            if (ajto != null) map.put("ajto", ajto);
            if (telefon != null) map.put("telefon", telefon);
            if (email != null) map.put("email", email);
            if (bankszamlaszam != null) map.put("bankszamlaszam", bankszamlaszam);
            if (extra != null) map.putAll(extra);
            return map;
        }
    }

    private void load() {
        profiles = new LinkedHashMap<>();
        if (Files.exists(storeFile)) {
            try {
                String json = Files.readString(storeFile, StandardCharsets.UTF_8);
                Type type = new TypeToken<Map<String, TaxpayerProfile>>() {}.getType();
                Map<String, TaxpayerProfile> loaded = gson.fromJson(json, type);
                if (loaded != null) profiles = loaded;
            } catch (Exception e) {
                System.err.println("Warning: could not load taxpayer store: " + e.getMessage());
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(storeFile.getParent());
            Files.writeString(storeFile, gson.toJson(profiles), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save taxpayer store: " + e.getMessage(), e);
        }
    }

    public void saveProfile(TaxpayerProfile profile) {
        profiles.put(profile.id(), profile);
        save();
    }

    public TaxpayerProfile getProfile(String id) {
        return profiles.get(id);
    }

    public TaxpayerProfile findByName(String name) {
        String lower = name.toLowerCase();
        for (TaxpayerProfile p : profiles.values()) {
            if (p.nev() != null && p.nev().toLowerCase().contains(lower)) return p;
            if (p.adoazonosito() != null && p.adoazonosito().equals(name)) return p;
        }
        return null;
    }

    public void deleteProfile(String id) {
        profiles.remove(id);
        save();
    }

    public List<TaxpayerProfile> listProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public int size() { return profiles.size(); }
}
