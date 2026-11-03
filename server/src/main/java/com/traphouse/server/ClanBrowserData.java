package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.traphouse.server.RankingStore.*;

/** Browser observations never award points, change identity, or change permissions. */
final class ClanBrowserData {
    static final Set<String> METRICS=new LinkedHashSet<>(Arrays.asList("clog","pets","points","greenlogs","xp","monthly-clog","monthly-points","clues","combat","bosses"));
    static final Set<String> TYPES=new HashSet<>(Arrays.asList("NORMAL","IRONMAN","ULTIMATE_IRONMAN","HARDCORE_IRONMAN","GROUP_IRONMAN","HARDCORE_GROUP_IRONMAN"));
    static long integer(JsonElement value,long max) {
        try {
            if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();
            long number=value.getAsBigDecimal().longValueExact();if(number<0||number>max)throw new IllegalArgumentException();return number;
        }catch(RuntimeException invalid){throw new Policy.Rejected(400,"Invalid browser observation");}
    }
    static String key(String value) {
        if(value==null||!value.matches("[a-z0-9_]{1,100}"))throw new Policy.Rejected(400,"Invalid category");return value;
    }
    static JsonObject object(String value){return JsonParser.parseString(value).getAsJsonObject();}
    static String text(JsonObject data,String name,String fallback){return data.has(name)&&!data.get(name).isJsonNull()?data.get(name).getAsString():fallback;}
    static Long number(JsonObject data,String name){return data.has(name)&&!data.get(name).isJsonNull()?data.get(name).getAsLong():null;}
    static JsonObject stats(JsonObject input) {
        JsonObject result=new JsonObject();
        for(Map.Entry<String,JsonElement> entry:input.entrySet()) {
            String name=entry.getKey();JsonElement value=entry.getValue();
            if(name.equals("accountType")) {
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!TYPES.contains(value.getAsString()))throw new Policy.Rejected(400,"Invalid account type");result.add(name,value);
            } else if(Arrays.asList("clog","pets","combat","xp").contains(name))result.addProperty(name,integer(value,1_000_000_000_000L));
            else if(name.equals("bosses")||name.equals("clues")) {
                if(!value.isJsonObject()||value.getAsJsonObject().size()>150)throw new Policy.Rejected(400,"Too many counters");
                JsonObject counters=new JsonObject();for(Map.Entry<String,JsonElement> counter:value.getAsJsonObject().entrySet())counters.addProperty(key(counter.getKey()),integer(counter.getValue(),1_000_000_000L));result.add(name,counters);
            } else throw new Policy.Rejected(400,"Unknown browser statistic");
        }
        return result;
    }
    static JsonObject sync(Connection c,Actor actor,JsonObject body)throws SQLException {
        if(!Policy.normalize(actor.name).equals(Policy.normalize(Policy.text(body,"player",12))))throw new Policy.Rejected(403,"Character mismatch");
        if(body.has("points")||body.has("memberId"))throw new Policy.Rejected(400,"Browser sync cannot change rewards or identity");
        lockMember(c,actor.id);
        if(body.has("stats")) {
            if(!body.get("stats").isJsonObject())throw new Policy.Rejected(400,"Invalid statistics");
            JsonObject data=stats(body.getAsJsonObject("stats"));
            saveStats(c,actor.id,data);
            if(data.has("clog")) {
                long count=integer(data.get("clog"),10000);
                execute(c,"INSERT INTO browser_monthly_collection(member_id,month,baseline,peak) VALUES (?,(date_trunc('month',now() AT TIME ZONE 'UTC'))::date,?,?) ON CONFLICT(member_id,month) DO UPDATE SET peak=greatest(browser_monthly_collection.peak,excluded.peak)",actor.id,count,count);
            }
        }
        if(body.has("pages")) {
            if(!body.get("pages").isJsonArray()||body.getAsJsonArray("pages").size()>5)throw new Policy.Rejected(400,"At most five collection pages per batch");
            for(JsonElement value:body.getAsJsonArray("pages"))savePage(c,actor.id,value.getAsJsonObject());
        }
        return result(actor.id.toString(),"SYNCED",0);
    }
    static void saveStats(Connection c,UUID member,JsonObject data)throws SQLException {
        execute(c,"INSERT INTO browser_statistics(member_id,statistics) VALUES (?,?::jsonb) ON CONFLICT(member_id) DO UPDATE SET statistics=browser_statistics.statistics || excluded.statistics,updated_at=now()",member,data.toString());
    }
    static JsonArray mergeItems(JsonArray input,JsonArray previous) {
        if(input.size()<1||input.size()>500)throw new Policy.Rejected(400,"Invalid collection page size");
        Map<Integer,JsonObject> old=new HashMap<>();for(JsonElement item:previous){JsonObject row=item.getAsJsonObject();old.put(row.get("id").getAsInt(),row);}
        Set<Integer> seen=new HashSet<>();JsonArray result=new JsonArray();
        for(JsonElement value:input) {
            JsonObject item=value.getAsJsonObject();int id=(int)integer(item.get("id"),100000);if(id==0||!seen.add(id))throw new Policy.Rejected(400,"Invalid or duplicate collection item");
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("name",item.has("name")?Policy.text(item,"name",100):old.containsKey(id)?text(old.get(id),"name","Item "+id):"Item "+id);
            Long quantity=item.has("quantity")&&!item.get("quantity").isJsonNull()?integer(item.get("quantity"),Integer.MAX_VALUE):null;
            Long prior=old.containsKey(id)?number(old.get(id),"quantity"):null;
            if(quantity==null)quantity=prior;else if(prior!=null)quantity=Math.max(quantity,prior);
            if(quantity!=null)row.addProperty("quantity",quantity);result.add(row);
        }
        return result;
    }
    static boolean complete(JsonArray items){if(items.size()==0)return false;for(JsonElement item:items){Long count=number(item.getAsJsonObject(),"quantity");if(count==null||count==0)return false;}return true;}
    private static void savePage(Connection c,UUID member,JsonObject page)throws SQLException {
        String key=key(Policy.text(page,"key",100)),label=Policy.text(page,"label",100),section=Policy.text(page,"section",32);
        if(!Arrays.asList("Bosses","Raids","Clues","Minigames","Other").contains(section))throw new Policy.Rejected(400,"Invalid collection section");
        JsonArray previous=new JsonArray();Long frozen=null,lastDrop=null,oldCounter=null;boolean wasComplete=false;
        try(PreparedStatement s=query(c,"SELECT items::text,completion_counter,completed,last_drop_counter,counter FROM browser_collection_pages WHERE member_id=? AND page_key=? FOR UPDATE",member,key);ResultSet r=s.executeQuery()) {
            if(r.next()){previous=JsonParser.parseString(r.getString(1)).getAsJsonArray();frozen=(Long)r.getObject(2);wasComplete=r.getBoolean(3);lastDrop=(Long)r.getObject(4);oldCounter=(Long)r.getObject(5);}
        }
        if(previous.size()==0)try(PreparedStatement s=query(c,"SELECT count(*) FROM browser_collection_pages WHERE member_id=?",member);ResultSet r=s.executeQuery()){r.next();if(r.getLong(1)>=250)throw new Policy.Rejected(422,"Collection page limit reached");}
        if(!page.has("items")||!page.get("items").isJsonArray())throw new Policy.Rejected(400,"Collection items required");
        JsonArray items=mergeItems(page.getAsJsonArray("items"),previous);boolean completed=complete(items);
        Long counter=page.has("counter")?integer(page.get("counter"),Integer.MAX_VALUE):null;
        if(counter==null)counter=oldCounter;else if(oldCounter!=null)counter=Math.max(counter,oldCounter);
        if(!completed)frozen=null;else if(!wasComplete)frozen=counter;
        if(page.has("lastDropCounter")) {
            long drop=integer(page.get("lastDropCounter"),Integer.MAX_VALUE);
            if(counter==null||drop>counter)throw new Policy.Rejected(400,"Drop counter exceeds current counter");
            lastDrop=lastDrop==null?drop:Math.max(lastDrop,drop);
        }
        execute(c,"INSERT INTO browser_collection_pages(member_id,page_key,label,section,items,counter,completion_counter,completed,last_drop_counter) VALUES (?,?,?,?,?::jsonb,?,?,?,?) ON CONFLICT(member_id,page_key) DO UPDATE SET label=excluded.label,section=excluded.section,items=excluded.items,counter=excluded.counter,completion_counter=excluded.completion_counter,completed=excluded.completed,last_drop_counter=excluded.last_drop_counter,updated_at=now()",member,key,label,section,items.toString(),counter,frozen,completed,lastDrop);
        if(key.equals("all_pets")) {
            int obtained=0;boolean known=true;for(JsonElement item:items){Long count=number(item.getAsJsonObject(),"quantity");if(count==null)known=false;else if(count>0)obtained++;}
            if(known){JsonObject pets=new JsonObject();pets.addProperty("pets",obtained);saveStats(c,member,pets);}
        }
    }
    static JsonObject catalogue(Connection c)throws SQLException {
        JsonObject data=new JsonObject();JsonArray pages=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT DISTINCT ON (p.page_key) p.page_key,p.label,p.section FROM browser_collection_pages p JOIN members m ON m.id=p.member_id WHERE m.active ORDER BY p.page_key,p.updated_at DESC LIMIT 500");ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject page=new JsonObject();page.addProperty("key",r.getString(1));page.addProperty("label",r.getString(2));page.addProperty("section",r.getString(3));pages.add(page);}
        }
        data.add("pages",pages);JsonArray accounts=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT name FROM members WHERE active ORDER BY normalized_name LIMIT 1000");ResultSet r=s.executeQuery()){while(r.next())accounts.add(r.getString(1));}
        data.add("accounts",accounts);data.addProperty("generatedAt",Instant.now().toString());return data;
    }
    static JsonObject view(Connection c,Map<String,String> request)throws SQLException {
        String mode=request.getOrDefault("mode","leaderboard"),metric=request.getOrDefault("metric","points"),category=request.get("category");
        if(mode.equals("catalogue"))return catalogue(c);
        if(mode.equals("clog"))return collection(c,request.get("player"),category);
        if(mode.equals("leaderboard"))return leaderboard(c,metric,category);
        return categoryBoard(c,mode,category);
    }
    private static JsonObject collection(Connection c,String player,String category)throws SQLException {
        if(player==null)throw new Policy.Rejected(400,"Choose a clan account");
        JsonObject data=new JsonObject();data.addProperty("kind","clog");data.addProperty("player",player);data.addProperty("generatedAt",Instant.now().toString());JsonArray pages=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT p.page_key,p.label,p.section,p.items::text,p.counter,p.completed,p.completion_counter FROM browser_collection_pages p JOIN members m ON m.id=p.member_id WHERE m.active AND m.normalized_name=? AND (?::text IS NULL OR p.page_key=?) ORDER BY p.section,p.label",Policy.normalize(player),category,category);ResultSet r=s.executeQuery()) {
            while(r.next()) {
                JsonObject page=new JsonObject();page.addProperty("key",r.getString(1));page.addProperty("label",r.getString(2));page.addProperty("section",r.getString(3));page.add("items",JsonParser.parseString(r.getString(4)));page.addProperty("counter",(Long)r.getObject(5));page.addProperty("complete",r.getBoolean(6));page.addProperty("completionCounter",(Long)r.getObject(7));pages.add(page);
            }
        }
        data.add("pages",pages);JsonArray completed=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT p.page_key FROM browser_collection_pages p JOIN members m ON m.id=p.member_id WHERE m.active AND m.normalized_name=? AND p.completed ORDER BY p.page_key",Policy.normalize(player));ResultSet r=s.executeQuery()){while(r.next())completed.add(r.getString(1));}
        data.add("completedPages",completed);return data;
    }
    static String group(String type) {
        if("NORMAL".equals(type))return "main";
        if("HARDCORE_IRONMAN".equals(type)||"HARDCORE_GROUP_IRONMAN".equals(type))return "hardcore";
        if(TYPES.contains(type))return "iron";return "unknown";
    }
    private static Map<String,JsonObject> statistics(Connection c)throws SQLException {
        Map<String,JsonObject> values=new HashMap<>();
        try(PreparedStatement s=query(c,"SELECT m.name,coalesce(s.statistics,'{}'::jsonb)::text,(SELECT count(*) FROM browser_collection_pages p WHERE p.member_id=m.id AND p.completed),(SELECT peak-baseline FROM browser_monthly_collection b WHERE b.member_id=m.id AND b.month=(date_trunc('month',now() AT TIME ZONE 'UTC'))::date),(SELECT coalesce(sum(e.points),0) FROM point_entries e WHERE e.member_id=m.id AND e.created_at>=date_trunc('month',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'),EXISTS(SELECT 1 FROM browser_collection_pages p WHERE p.member_id=m.id AND jsonb_path_exists(p.items,'$[*].quantity')) FROM members m LEFT JOIN browser_statistics s ON s.member_id=m.id WHERE m.active ORDER BY m.normalized_name LIMIT 1000");ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject stats=object(r.getString(2));if(r.getBoolean(6))stats.addProperty("greenlogs",r.getLong(3));if(r.getObject(4)!=null)stats.addProperty("monthly-clog",r.getLong(4));stats.addProperty("monthly-points",r.getLong(5));values.put(r.getString(1),stats);}
        }
        return values;
    }
    private static Long score(JsonObject stats,String metric,String category) {
        if(!metric.equals("bosses")&&!metric.equals("clues"))return number(stats,metric);
        if(!stats.has(metric))return null;JsonObject counters=stats.getAsJsonObject(metric);
        if(category!=null&&!category.equals("all"))return number(counters,category);
        if(counters.has("all"))return number(counters,"all");long sum=0;for(JsonElement value:counters.asMap().values())sum+=value.getAsLong();return sum;
    }
    private static JsonObject leaderboard(Connection c,String metric,String category)throws SQLException {
        Map<String,JsonObject> statistics=statistics(c);List<JsonObject> rows=new ArrayList<>();int missing=0;
        for(JsonElement element:publicLeaderboard(c).getAsJsonArray("leaderboard")) {
            JsonObject source=element.getAsJsonObject();String name=source.get("name").getAsString();JsonObject stats=statistics.getOrDefault(name,new JsonObject());
            Long value=metric.equals("points")?Long.valueOf(source.get("points").getAsLong()):score(stats,metric,category);
            if(value==null){missing++;continue;}
            JsonObject row=new JsonObject();row.addProperty("player",name);row.addProperty("value",value);row.addProperty("group",group(text(stats,"accountType","")));rows.add(row);
        }
        rows.sort(Comparator.<JsonObject>comparingLong(row->row.get("value").getAsLong()).reversed().thenComparing(row->Policy.normalize(row.get("player").getAsString())));
        JsonObject data=board("leaderboard",metric);data.add("panels",panels(rows));data.addProperty("missing",missing);
        JsonArray choices=new JsonArray();SortedSet<String> keys=new TreeSet<>();
        if(metric.equals("bosses")||metric.equals("clues"))for(JsonObject stats:statistics.values())if(stats.has(metric))keys.addAll(stats.getAsJsonObject(metric).keySet());
        for(String key:keys)choices.add(key);data.add("choices",choices);return data;
    }
    private static JsonObject categoryBoard(Connection c,String mode,String category)throws SQLException {
        if(category==null)throw new Policy.Rejected(400,"Choose a collection category");
        List<JsonObject> rows=new ArrayList<>();int missing=0;boolean hasCounter=false;
        try(PreparedStatement s=query(c,"SELECT m.name,coalesce(s.statistics->>'accountType',''),p.items::text,p.counter,p.completion_counter,p.completed,p.completion_estimated,p.last_drop_counter FROM browser_collection_pages p JOIN members m ON m.id=p.member_id LEFT JOIN browser_statistics s ON s.member_id=m.id WHERE m.active AND p.page_key=? ORDER BY m.normalized_name LIMIT 1000",category);ResultSet r=s.executeQuery()) {
            while(r.next()) {
                Long current=(Long)r.getObject(4),frozen=(Long)r.getObject(5),last=(Long)r.getObject(8);boolean completed=r.getBoolean(6);
                if(current!=null)hasCounter=true;
                JsonArray items=JsonParser.parseString(r.getString(3)).getAsJsonArray();int obtained=0;for(JsonElement item:items){Long count=number(item.getAsJsonObject(),"quantity");if(count!=null&&count>0)obtained++;}
                Long value;
                if(mode.equals("dryboard")){if(current==null||last==null||current<last){missing++;continue;}value=current-last;}
                else value=completed?frozen:null;
                JsonObject row=new JsonObject();row.addProperty("player",r.getString(1));row.addProperty("group",group(r.getString(2)));row.addProperty("value",value);row.addProperty("complete",completed);row.addProperty("obtained",obtained);row.addProperty("total",items.size());row.addProperty("estimated",r.getBoolean(7));rows.add(row);
            }
        }
        if(mode.equals("dryboard"))rows.sort(Comparator.<JsonObject>comparingLong(row->row.get("value").getAsLong()).reversed().thenComparing(row->row.get("player").getAsString()));
        else rows.sort((a,b)->{
            int complete=Boolean.compare(b.get("complete").getAsBoolean(),a.get("complete").getAsBoolean());if(complete!=0)return complete;
            Long av=number(a,"value"),bv=number(b,"value");if(av!=null&&bv!=null){int compare=Long.compare(av,bv);if(!mode.equals("luck"))compare=-compare;if(compare!=0)return compare;}
            else if(av!=null)return -1;else if(bv!=null)return 1;
            int progress=Double.compare(b.get("obtained").getAsDouble()/b.get("total").getAsDouble(),a.get("obtained").getAsDouble()/a.get("total").getAsDouble());return progress!=0?progress:a.get("player").getAsString().compareToIgnoreCase(b.get("player").getAsString());
        });
        JsonObject data=board(mode,"counter");data.addProperty("category",category);data.addProperty("missing",missing);
        if(mode.equals("dryboard"))data.add("panels",panels(rows));else {
            int completed=0;for(JsonObject row:rows)if(row.get("complete").getAsBoolean())completed++;
            if(rows.size()>Math.max(10,completed))rows=new ArrayList<>(rows.subList(0,Math.max(10,completed)));
            JsonArray ranked=ranked(rows);if(!hasCounter)for(JsonElement value:ranked)if(value.getAsJsonObject().get("complete").getAsBoolean())value.getAsJsonObject().addProperty("rank",1);
            JsonArray panels=new JsonArray();JsonObject panel=new JsonObject();panel.addProperty("key","all");panel.addProperty("label",mode.equals("luck")?"Luckiest greenlogs":"Most deserved greenlogs");panel.add("rows",ranked);panels.add(panel);data.add("panels",panels);
        }
        return data;
    }
    private static JsonObject board(String kind,String metric){JsonObject data=new JsonObject();data.addProperty("kind",kind);data.addProperty("metric",metric);data.addProperty("generatedAt",Instant.now().toString());return data;}
    private static JsonArray ranked(List<JsonObject> rows){JsonArray result=new JsonArray();int rank=0;Long previous=null;for(int i=0;i<rows.size();i++){JsonObject row=rows.get(i).deepCopy();Long value=number(row,"value");if(value!=null){if(previous==null||!previous.equals(value))rank=i+1;row.addProperty("rank",rank);previous=value;}result.add(row);}return result;}
    private static JsonArray panels(List<JsonObject> rows) {
        JsonArray result=new JsonArray();String[][] groups={{"main","Mains"},{"iron","Ironman + GIM + UIM"},{"hardcore","Hardcore Ironman"},{"unknown","Account type not synced"}};
        for(String[] group:groups){List<JsonObject> selected=new ArrayList<>();for(JsonObject row:rows)if(group[0].equals(text(row,"group","unknown")))selected.add(row);if(group[0].equals("unknown")&&selected.isEmpty())continue;JsonObject panel=new JsonObject();panel.addProperty("key",group[0]);panel.addProperty("label",group[1]);panel.add("rows",ranked(selected));result.add(panel);}return result;
    }
}
