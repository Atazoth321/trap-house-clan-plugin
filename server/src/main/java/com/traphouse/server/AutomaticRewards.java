package com.traphouse.server;

import java.sql.*;
import java.util.*;

/** Named allowlist, never a wildcard reward for arbitrary loot or chat. */
final class AutomaticRewards {
    static final class Rule {
        final String type,key,name,category;
        final int points,limit;
        Rule(String type,String key,String name,String category,int points,int limit) {
            this.type=type;this.key=key;this.name=name;this.category=category;this.points=points;this.limit=limit;
        }
    }
    static List<Rule> rules() {
        List<Rule> rules=new ArrayList<>();
        // Real encounter NPC IDs only; exclude pets, quest encounters, and arena copies.
        loot(rules,new int[]{415,416,7241},4151,"Abyssal whip",100);
        loot(rules,new int[]{2215},11832,"Bandos chestplate",300);
        loot(rules,new int[]{2215},11834,"Bandos tassets",300);
        loot(rules,new int[]{2215},11836,"Bandos boots",150);
        loot(rules,new int[]{2205},11785,"Armadyl crossbow",350);
        loot(rules,new int[]{2205},11814,"Saradomin hilt",300);
        loot(rules,new int[]{3162},11826,"Armadyl helmet",200);
        loot(rules,new int[]{3162},11828,"Armadyl chestplate",300);
        loot(rules,new int[]{3162},11830,"Armadyl chainskirt",300);
        loot(rules,new int[]{3129},11824,"Zamorakian spear",250);
        loot(rules,new int[]{2042,2043,2044},12922,"Tanzanite fang",250);
        loot(rules,new int[]{2042,2043,2044},12932,"Magic fang",250);
        loot(rules,new int[]{2042,2043,2044},12927,"Serpentine visage",200);
        loot(rules,new int[]{8058,8059,8060,8061},11286,"Draconic visage",300);
        loot(rules,new int[]{8058,8059,8060,8061},22006,"Skeletal visage",500);
        loot(rules,new int[]{8058,8059,8060,8061},22111,"Dragonbone necklace",200);
        loot(rules,new int[]{494},11905,"Trident of the seas (full)",150);
        loot(rules,new int[]{494},12004,"Kraken tentacle",150);
        loot(rules,new int[]{5862,5863,5866},13231,"Primordial crystal",300);
        loot(rules,new int[]{5862,5863,5866},13229,"Pegasian crystal",200);
        loot(rules,new int[]{5862,5863,5866},13227,"Eternal crystal",200);
        loot(rules,new int[]{5862,5863,5866},13233,"Smouldering stone",150);
        loot(rules,new int[]{499},12002,"Occult necklace",150);
        loot(rules,new int[]{8615,8616,8617,8618,8619,8620,8621,8622},22966,"Hydra's claw",500);
        loot(rules,new int[]{8615,8616,8617,8618,8619,8620,8621,8622},22983,"Hydra leather",300);

        raid(rules,"chambers of xeric",new int[]{20997,21003,21043,21018,21021,21024,21015,21012,21034,21079,13652},
            new String[]{"Twisted bow","Elder maul","Kodai insignia","Ancestral hat","Ancestral robe top","Ancestral robe bottom","Dinh's bulwark","Dragon hunter crossbow","Dexterous prayer scroll","Arcane prayer scroll","Dragon claws"});
        raid(rules,"theatre of blood",new int[]{22486,22324,22481,22477,22326,22327,22328},
            new String[]{"Scythe of vitur (uncharged)","Ghrazi rapier","Sanguinesti staff (uncharged)","Avernic defender hilt","Justiciar faceguard","Justiciar chestguard","Justiciar legguards"});
        raid(rules,"tombs of amascut",new int[]{27277,26219,25985,25975,27226,27229,27232},
            new String[]{"Tumeken's shadow (uncharged)","Osmumten's fang","Elidinis' ward","Lightbearer","Masori mask","Masori body","Masori chaps"});

        // Collection observations are semantically unique per member in schema.sql.
        // Never award from the generic "funny feeling" message: it has no pet identity.
        for(String pet:new String[]{
            "Abyssal orphan","Baby chinchompa","Baby mole","Beaver","Bloodhound",
            "Callisto cub","Chompy chick","Giant squirrel","Hellpuppy","Herbi","Heron",
            "Ikkle hydra","Jal-nib-rek","Kalphite princess","Noon","Olmlet",
            "Pet chaos elemental","Pet dagannoth prime","Pet dagannoth rex","Pet dagannoth supreme",
            "Pet dark core","Pet general graardor","Pet k'ril tsutsaroth","Pet kraken",
            "Pet kree'arra","Pet penance queen","Pet smoke devil","Pet snakeling","Pet zilyana",
            "Phoenix","Prince black dragon","Rift guardian","Rock golem","Rocky",
            "Scorpia's offspring","Skotos","Smolcano","Sraracha","Tangleroot","Tzrek-jad",
            "Venenatis spiderling","Vet'ion jr.","Vorki","Youngllef","Lil' zik",
            "Little nightmare","Lil' creator","Tiny tempor","Abyssal protector",
            "Tumeken's guardian","Muphin","Butch","Baron","Lil'viathan","Wisp"}) {
            rules.add(new Rule("COLLECTION",Policy.normalize(pet),pet,"Pets",750,1));
        }
        rules.addAll(expandedRules());
        return Collections.unmodifiableList(rules);
    }
    static List<Rule> expandedRules() {
        List<Rule> rules=new ArrayList<>();
        loot(rules,new int[]{2267},6737,"Berserker ring",200);
        loot(rules,new int[]{2267},6735,"Warrior ring",100);
        loot(rules,new int[]{2266},6731,"Seers ring",150);
        loot(rules,new int[]{2265},6733,"Archers ring",200);
        loot(rules,new int[]{2265,2266,2267},6739,"Dragon axe",100);
        loot(rules,new int[]{319},12819,"Elysian sigil",1000);
        loot(rules,new int[]{319},12823,"Spectral sigil",600);
        loot(rules,new int[]{319},12827,"Arcane sigil",750);
        loot(rules,new int[]{319},12829,"Spirit shield",100);
        loot(rules,new int[]{319},12833,"Holy elixir",150);
        loot(rules,new int[]{239},11286,"Draconic visage",300);
        loot(rules,new int[]{963,965},3140,"Dragon chainbody",200);
        loot(rules,new int[]{963,965,2054,6609,6610,6611,6612,11992,11993,11994,11998},7158,"Dragon 2h sword",100);
        loot(rules,new int[]{2054,239,6609,6610,6611,6612,11992,11993,11994,11998},11920,"Dragon pickaxe",200);
        loot(rules,new int[]{8713},23528,"Sarachnis cudgel",200);
        loot(rules,new int[]{2215},11812,"Bandos hilt",300);
        loot(rules,new int[]{3162},11810,"Armadyl hilt",350);
        loot(rules,new int[]{3129},11816,"Zamorak hilt",250);
        loot(rules,new int[]{2215,2205,3162,3129},11818,"Godsword shard 1",100);
        loot(rules,new int[]{2215,2205,3162,3129},11820,"Godsword shard 2",100);
        loot(rules,new int[]{2215,2205,3162,3129},11822,"Godsword shard 3",100);
        int[] nex={11278,11279,11280,11281,11282};
        loot(rules,nex,26376,"Torva full helm (damaged)",750);
        loot(rules,nex,26378,"Torva platebody (damaged)",900);
        loot(rules,nex,26380,"Torva platelegs (damaged)",800);
        loot(rules,nex,26372,"Nihil horn",1000);
        loot(rules,nex,26370,"Ancient hilt",500);
        int[] muspah={12077,12078,12079,12080,12082};
        loot(rules,muspah,27614,"Venator shard",350);
        loot(rules,muspah,27627,"Ancient icon",150);
        int[] bear={6609,11992},spider={6610,11998},skeleton={6611,6612,11993,11994};
        loot(rules,bear,27681,"Voidwaker hilt",600);
        loot(rules,spider,27687,"Voidwaker gem",600);
        loot(rules,skeleton,27684,"Voidwaker blade",600);
        loot(rules,bear,27667,"Claws of callisto",300);
        loot(rules,spider,27670,"Fangs of venenatis",300);
        loot(rules,skeleton,27673,"Skull of vet'ion",300);
        loot(rules,bear,12603,"Tyrannical ring",200);
        loot(rules,spider,12605,"Treasonous ring",200);
        loot(rules,skeleton,12601,"Ring of the gods",300);
        loot(rules,new int[]{12166,12167},28281,"Magus vestige",600);
        loot(rules,new int[]{12223,12224},28285,"Ultor vestige",600);
        loot(rules,new int[]{12214,12215},28283,"Venator vestige",600);
        loot(rules,new int[]{12204,12205},28279,"Bellator vestige",600);
        int[] dt2={12166,12167,12223,12224,12214,12215,12204,12205};
        loot(rules,dt2,26241,"Virtus mask",500);
        loot(rules,dt2,26243,"Virtus robe top",600);
        loot(rules,dt2,26245,"Virtus robe bottom",600);
        loot(rules,dt2,28276,"Chromium ingot",150);
        int[] barrows={4708,4710,4712,4714,4716,4718,4720,4722,4724,4726,4728,4730,4732,4734,4736,4738,4745,4747,4749,4751,4753,4755,4757,4759};
        String[] names={"Ahrim's hood","Ahrim's staff","Ahrim's robetop","Ahrim's robeskirt","Dharok's helm","Dharok's greataxe","Dharok's platebody","Dharok's platelegs","Guthan's helm","Guthan's warspear","Guthan's platebody","Guthan's chainskirt","Karil's coif","Karil's crossbow","Karil's leathertop","Karil's leatherskirt","Torag's helm","Torag's hammers","Torag's platebody","Torag's platelegs","Verac's helm","Verac's flail","Verac's brassard","Verac's plateskirt"};
        for(int i=0;i<barrows.length;i++)rules.add(new Rule("LOOT","chest:barrows:"+barrows[i],names[i],"Drops",50,2));
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    static List<Rule> testDropRules() {
        List<Rule> rules=new ArrayList<>();
        // Ordinary cows only, excluding calves, quest encounters and minigames.
        for(int npc:new int[]{2790,2791,2793,2795}) {
            rules.add(new Rule("LOOT",npc+":1739","Cowhide (easy test drop)","Drops",1,1));
        }
        return Collections.unmodifiableList(rules);
    }
    private static void raid(List<Rule> rules,String source,int[] items,String[] names) {
        for(int i=0;i<items.length;i++) {
            boolean mega=items[i]==20997||items[i]==22486||items[i]==27277;
            rules.add(new Rule("LOOT","chest:"+source+":"+items[i],names[i],"Drops",mega?1000:500,2));
        }
    }
    static void seed(Connection c)throws SQLException {
        seed(c,"automatic-rewards-v1",rules());
        // Existing v1 installations must still receive the new catalog. Insert only
        // expansion keys so previously removed base rules are not resurrected.
        seed(c,"automatic-rewards-v2",expandedRules());
        lowerBarrowsRewards(c);
        seed(c,"easy-test-cowhide-v1",testDropRules());
    }
    private static void lowerBarrowsRewards(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        String migration="barrows-rewards-v1";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        // Future awards only: never deduct points or rewrite historical ledger entries.
        // Preserve disabled rules, custom amounts, and all existing daily limits.
        for(Rule rule:expandedRules())if(rule.key.startsWith("chest:barrows:")) {
            RankingStore.execute(c,"UPDATE rules SET points=? WHERE type='LOOT' AND event_key=? AND enabled AND points=150",rule.points,rule.key);
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
    private static void seed(Connection c,String migration,List<Rule> catalog)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()) {if(r.next())return;}
        for(Rule rule:catalog) {
            // Respect custom amounts, categories, caps, and deliberately disabled rules.
            RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES (?,?,?,?,true,?,?) ON CONFLICT(type,event_key) DO NOTHING",
                rule.type,rule.key,rule.points,rule.limit,rule.category,rule.name);
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
}
