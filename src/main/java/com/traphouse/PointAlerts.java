package com.traphouse;

import com.google.gson.*;
import java.util.*;
import net.runelite.client.util.Text;

/** Session-local cursor: old awards stay silent and repeated pages cannot repeat chat. */
final class PointAlerts {
    private Long cursor;
    private static final class Notice {
        final String text,category;
        Notice(String text,String category){this.text=text;this.category=category;}
    }
    private final ArrayDeque<Notice> messages=new ArrayDeque<>();

    Long cursor(){return cursor;}
    void reset(){cursor=null;messages.clear();}
    void accept(JsonObject data) {
        long next=Long.parseLong(data.get("cursor").getAsString());
        JsonArray awards=data.getAsJsonArray("awards");
        if(next<0||awards==null||awards.size()>100)throw new IllegalArgumentException("Invalid award page");
        if(cursor==null) {
            if(awards.size()!=0)throw new IllegalArgumentException("Expected award baseline");
            cursor=next;return;
        }
        if(next<cursor)throw new IllegalArgumentException("Award cursor moved backwards");
        if(next==cursor)return;
        long previous=cursor;List<Notice> pending=new ArrayList<>();
        for(JsonElement element:awards) {
            JsonObject award=element.getAsJsonObject();
            long id=Long.parseLong(award.get("id").getAsString());
            int points=award.get("points").getAsBigDecimal().intValueExact();
            if(id<=previous||id>next||points<=0)throw new IllegalArgumentException("Invalid confirmed award");
            String title=Text.removeTags(award.get("title").getAsString()).replaceAll("[\\p{Cntrl}<>]"," ").trim();
            if(title.isEmpty())title="a clan reward";
            if(title.length()>180)title=title.substring(0,177)+"...";
            String category=award.has("category")&&!award.get("category").isJsonNull()?award.get("category").getAsString():"Misc";
            pending.add(new Notice("[Trap House] You earned "+points+" clan point"+(points==1?"":"s")+" for "+title+".",category));previous=id;
        }
        if(previous!=next)throw new IllegalArgumentException("Missing award entries");
        // Leave the cursor untouched if chat is backed up; the next sync retries the page.
        if(messages.size()+pending.size()>200)return;
        messages.addAll(pending);cursor=next;
    }
    List<String> drain() {return drain(null);}
    List<String> drain(TrapHouseConfig config) {
        List<String> batch=new ArrayList<>();
        while(batch.size()<5&&!messages.isEmpty()) {
            Notice notice=messages.removeFirst();String text=notice.text;
            if(config!=null&&config.rankingPointMessageColors()) {
                java.awt.Color color;
                switch(notice.category) {
                    case "Drops":color=config.rankingDropMessageColor();break;
                    case "Pets":color=config.rankingPetMessageColor();break;
                    case "Milestones":color=config.rankingMilestoneMessageColor();break;
                    default:color=config.rankingOtherMessageColor();
                }
                if(color!=null)text="<col="+String.format(Locale.ROOT,"%06x",color.getRGB()&0xffffff)+">"+text+"</col>";
            }
            batch.add(text);
        }
        return batch;
    }
}
