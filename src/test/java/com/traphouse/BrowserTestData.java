package com.traphouse;
import com.google.gson.*;
import java.time.Instant;

/** Illustrative browser fixtures, never shipped in the plugin. */
final class BrowserTestData {
    static JsonObject view(String metric) {
        JsonObject data=new JsonObject();data.addProperty("kind","leaderboard");data.addProperty("metric",metric);data.addProperty("generatedAt",Instant.now().toString());data.addProperty("missing",0);JsonArray panels=new JsonArray();
        String[] keys={"main","iron","hardcore"},labels={"Mains","Ironman + GIM + UIM","Hardcore Ironman"};
        for(int group=0;group<3;group++){JsonObject panel=new JsonObject();panel.addProperty("key",keys[group]);panel.addProperty("label",labels[group]);JsonArray rows=new JsonArray();for(int i=0;i<20;i++){JsonObject row=new JsonObject();row.addProperty("rank",i+1);row.addProperty("player",(group==0?"SampleMain":group==1?"SampleIron":"SampleHC")+(i+1));row.addProperty("value",metric.equals("xp")?5_000_000_000L-group*1_000_000_000L-i*200_000_000L:metric.equals("points")?31931-group*6000-i*1103:1000-group*100-i*40);rows.add(row);}panel.add("rows",rows);panels.add(panel);}
        data.add("panels",panels);data.add("choices",new Gson().fromJson("['vorkath','zulrah','chambers_of_xeric']",JsonArray.class));return data;
    }
    static JsonObject catalogue(){return new Gson().fromJson("{pages:[{key:'vorkath',label:'Vorkath',section:'Bosses'},{key:'zulrah',label:'Zulrah',section:'Bosses'},{key:'chambers_of_xeric',label:'Chambers of Xeric',section:'Raids'},{key:'hard_treasure_trails',label:'Hard Treasure Trails',section:'Clues'}]}",JsonObject.class);}
    static JsonObject log(String player,String key){JsonObject data=new Gson().fromJson("{kind:'clog',pages:[{key:'vorkath',label:'Vorkath',counter:524,complete:false,items:[{id:21992,name:'Vorki',quantity:1},{id:11286,name:'Draconic visage',quantity:0},{id:22006,name:'Skeletal visage'},{id:21907,name:'Vorkath head',quantity:5},{id:22111,name:'Dragonbone necklace',quantity:1}]}],completedPages:[]}",JsonObject.class);data.addProperty("player",player);data.addProperty("generatedAt",Instant.now().toString());return data;}
}
