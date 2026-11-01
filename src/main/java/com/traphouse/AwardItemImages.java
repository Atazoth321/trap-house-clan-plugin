package com.traphouse;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/** Resolves the full game item catalogue, including untradeable collection items and pets. */
final class AwardItemImages implements PointsLogPanel.ItemArt {
    private final Client client;
    private final ClientThread thread;
    private final ItemManager items;
    private CompletableFuture<Map<String,Integer>> catalogue;
    @Inject AwardItemImages(Client client,ClientThread thread,ItemManager items){this.client=client;this.thread=thread;this.items=items;}

    static int itemId(JsonObject entry) {
        try {
            if(entry.has("itemId")){int id=entry.get("itemId").getAsInt();return id>0?id:-1;}
            String type=value(entry,"sourceType"),key=value(entry,"sourceKey");
            if(type.equals("COLLECTION")&&PetTracker.itemId(key)>0)return PetTracker.itemId(key);
            if(type.equals("LOOT")&&key.matches("[0-9]+:[0-9]+"))return Integer.parseInt(key.substring(key.indexOf(':')+1));
            if(type.equals("LOOT")&&key.matches("clue:(beginner|easy|medium|hard|elite|master):[0-9]{1,6}"))return Integer.parseInt(key.substring(key.lastIndexOf(':')+1));
            if(type.equals("COLLECTION")&&key.matches("clue milestone:(beginner|easy|medium|hard|elite|master):(10|50|100)"))key="clue completion:"+key.split(":")[1];
            if(type.equals("LOOT")&&key.matches("chest:(chambers of xeric|theatre of blood|tombs of amascut|barrows|lunar chest):[0-9]+"))return Integer.parseInt(key.substring(key.lastIndexOf(':')+1));
            if(type.equals("COLLECTION"))switch(key) {
                case "clue completion:beginner":return 23245;
                case "clue completion:easy":return 20546;
                case "clue completion:medium":return 20545;
                case "clue completion:hard":return 20544;
                case "clue completion:elite":return 20543;
                case "clue completion:master":return 19836;
                default:break;
            }
        }catch(RuntimeException invalid){return -1;}return -1;
    }
    static String itemName(JsonObject entry) {
        String category=value(entry,"category"),type=value(entry,"sourceType");
        if(!type.equals("COLLECTION")&&!Arrays.asList("Drops","Pets","Collection").contains(category))return "";
        return type.equals("COLLECTION")?value(entry,"sourceKey"):value(entry,"title");
    }
    private static String value(JsonObject e,String key){return e.has(key)&&!e.get(key).isJsonNull()?e.get(key).getAsString():"";}
    public void load(JsonObject entry,BiConsumer<String,Icon> done) {
        int id=itemId(entry);
        if(id>0){render(id,done);return;}
        String name=itemName(entry);if(name.isEmpty())return;
        catalogue().thenAccept(index->{Integer found=index.get(RankingTracker.normalize(name));if(found!=null)render(found,done);});
    }
    private synchronized CompletableFuture<Map<String,Integer>> catalogue() {
        if(catalogue!=null)return catalogue;
        CompletableFuture<Map<String,Integer>> result=new CompletableFuture<>();catalogue=result;
        Map<String,Integer> index=new HashMap<>();int[] cursor={0};
        // Yield between chunks so a profile lookup cannot stall a game tick.
        thread.invokeLater(()->{
            if(client.getGameState().ordinal()<GameState.LOGIN_SCREEN.ordinal())return false;
            int count=client.getItemCount(),end=Math.min(count,cursor[0]+256);
            while(cursor[0]<end){int id=cursor[0]++;ItemComposition item=items.getItemComposition(id);String name=item.getName();
                if(name!=null&&!name.equalsIgnoreCase("null")&&item.getNote()==-1&&item.getPlaceholderTemplateId()==-1)index.putIfAbsent(RankingTracker.normalize(name),id);
            }
            if(cursor[0]<count)return false;result.complete(Collections.unmodifiableMap(index));return true;
        });return result;
    }
    private void render(int id,BiConsumer<String,Icon> done) {
        thread.invokeLater(()->{
            if(client.getGameState().ordinal()<GameState.LOGIN_SCREEN.ordinal())return false;
            if(id>=client.getItemCount())return true;
            String name=items.getItemComposition(id).getName();if(name==null||name.equalsIgnoreCase("null"))return true;
            AsyncBufferedImage image=items.getImage(id);if(image!=null)image.onLoaded(()->SwingUtilities.invokeLater(()->done.accept(name,new ImageIcon(image))));return true;
        });
    }
}
