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
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    private static void raid(List<Rule> rules,String source,int[] items,String[] names) {
        for(int i=0;i<items.length;i++) {
            boolean mega=items[i]==20997||items[i]==22486||items[i]==27277;
            rules.add(new Rule("LOOT","chest:"+source+":"+items[i],names[i],"Drops",mega?1000:500,2));
        }
    }
    static void seed(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name='automatic-rewards-v1'");ResultSet r=s.executeQuery()) {if(r.next())return;}
        for(Rule rule:rules()) {
            // Respect custom amounts, categories, caps, and deliberately disabled rules.
            RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES (?,?,?,?,true,?,?) ON CONFLICT(type,event_key) DO NOTHING",
                rule.type,rule.key,rule.points,rule.limit,rule.category,rule.name);
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('automatic-rewards-v1')");
    }
}
