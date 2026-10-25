package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Explicitly labelled test-fixture preview of real Swing components; never packaged with the plugin. */
public final class TrapHouseClanPreview {
    public static void main(String[] args)throws Exception {
        JsonObject sample;
        try(InputStream input=TrapHouseClanPreview.class.getResourceAsStream("/clan-preview.json")) {
            sample=new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),JsonObject.class);
        }
        SwingUtilities.invokeAndWait(()-> {
            RuneLiteLAF.setup();
            int width=260,height=1000;
            BufferedImage image=new BufferedImage(width*4,height,BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=image.createGraphics();graphics.setColor(ClanStyle.BACKGROUND);graphics.fillRect(0,0,image.getWidth(),image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int[] selection={0,3,2,5};
            for(int i=0;i<selection.length;i++) {
                RankingPanel ranking=new RankingPanel(()->{});
                TrapHouseDashboard panel=new TrapHouseDashboard(new TrapHousePanel(new TrapHouseConfig(){},()->{}),new TrapHousePanel(new TrapHouseConfig(){},()->{},true),selection[i],ranking);
                previewActions(ranking,panel,sample);
                ranking.showData(sample.deepCopy());panel.setSize(width-8,height-35);layout(panel);
                Graphics2D page=(Graphics2D)graphics.create(i*width+4,30,width-8,height-35);panel.printAll(page);page.dispose();
            }
            graphics.setColor(ClanStyle.RED);graphics.setFont(new Font("SansSerif",Font.BOLD,13));graphics.drawString("TRAP HOUSE / DEVELOPMENT PREVIEW / SAMPLE DATA — NOT LIVE CLAN STANDINGS",10,19);graphics.dispose();
            try{ImageIO.write(image,"png",new File("build/trap-house-clan-preview.png"));}catch(IOException error){throw new RuntimeException(error);}
            rankPreview(sample);
            PointsLogPanel log=new PointsLogPanel(sample.getAsJsonObject("profile").get("name").getAsString(),"Drops",(query,done)->done.accept(previewLog(sample,query)),TrapHouseClanPreview::previewItem);
            log.setSize(900,680);layout(log);BufferedImage logImage=new BufferedImage(900,710,BufferedImage.TYPE_INT_RGB);
            Graphics2D lg=logImage.createGraphics();lg.setColor(ProfileStyle.BACKGROUND);lg.fillRect(0,0,900,710);lg.setColor(ProfileStyle.ACCENT);lg.drawString("TRAP HOUSE / POINTS LOG / SAMPLE DATA",12,20);lg.translate(0,30);log.printAll(lg);lg.dispose();
            try{ImageIO.write(logImage,"png",new File("build/trap-house-points-log-preview.png"));}catch(IOException error){throw new RuntimeException(error);}
            if(java.util.Arrays.asList(args).contains("--all")) {
                gallery(sample,new int[]{0,2},new String[]{"PROFILE","LEADERBOARD"},1250,"members");
                gallery(sample,new int[]{0,0},new String[]{"MY PROFILE","MEMBER PROFILE"},1100,"profiles");
                gallery(sample,new int[]{3,5,6},new String[]{"ACHIEVEMENTS","DIARY","COMPETITIONS"},1200,"features");
                gallery(sample,new int[]{4,4,4},new String[]{"BOSS EVENT","SKILL EVENT","CLAN EVENTS"},1700,"events");
            }
            if(java.util.Arrays.asList(args).contains("--window")||java.util.Arrays.asList(args).contains("--items")) {
                RankingPanel ranking=new RankingPanel(()->{});
                TrapHouseDashboard panel=new TrapHouseDashboard(new TrapHousePanel(new TrapHouseConfig(){},()->{}),new TrapHousePanel(new TrapHouseConfig(){},()->{},true),0,ranking);
                previewActions(ranking,panel,sample);
                ranking.showData(sample.deepCopy());JFrame frame=new JFrame("TRAP HOUSE / MEMBER PROFILE PREVIEW / SAMPLE DATA");frame.add(panel);frame.setSize(300,950);frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.setVisible(true);
                if(java.util.Arrays.asList(args).contains("--items")) {
                    frame.setLocation(20,20);layout(panel);openDrops(ranking);
                    for(Window owned:frame.getOwnedWindows())if(owned instanceof JDialog) {
                        JDialog popup=(JDialog)owned;popup.setLocation(330,80);popup.setTitle(popup.getTitle()+" / SAMPLE DATA");popup.toFront();
                        JPanel content=(JPanel)popup.getContentPane();layout(content);layout(panel);
                        BufferedImage example=new BufferedImage(1220,1000,BufferedImage.TYPE_INT_RGB);Graphics2D eg=example.createGraphics();
                        eg.setColor(ProfileStyle.BACKGROUND);eg.fillRect(0,0,1220,1000);eg.setColor(ProfileStyle.ACCENT);eg.drawString("TRAP HOUSE / PLUGIN COMPONENTS / SAMPLE DATA",12,20);
                        Graphics2D sidebar=(Graphics2D)eg.create(8,35,300,950);panel.printAll(sidebar);sidebar.dispose();
                        Graphics2D detail=(Graphics2D)eg.create(320,95,900,680);content.printAll(detail);detail.dispose();eg.dispose();
                        try{ImageIO.write(example,"png",new File("build/trap-house-plugin-items-example.png"));}catch(IOException error){throw new RuntimeException(error);}
                    }
                }
            }
        });
    }
    private static void openDrops(Container container) {
        for(Component child:container.getComponents()) {
            if(child instanceof JButton&&"Drops points log".equals(((JButton)child).getAccessibleContext().getAccessibleName())){((JButton)child).doClick();return;}
            if(child instanceof Container)openDrops((Container)child);
        }
    }
    private static void gallery(JsonObject sample,int[] selections,String[] titles,int height,String name) {
        int width=310;
        BufferedImage image=new BufferedImage(width*selections.length,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();graphics.setColor(ClanStyle.BACKGROUND);graphics.fillRect(0,0,image.getWidth(),height);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font("SansSerif",Font.BOLD,12));graphics.setColor(ClanStyle.RED);
        graphics.drawString("TRAP HOUSE / SAMPLE DATA / DEVELOPMENT PREVIEW",10,18);
        for(int i=0;i<selections.length;i++) {
            RankingPanel ranking=new RankingPanel(()->{});
            TrapHousePanel boss=new TrapHousePanel(new TrapHouseConfig(){},()->{}),skill=new TrapHousePanel(new TrapHouseConfig(){},()->{},true);
            java.time.Instant now=java.time.Instant.now();
            boss.updateEvent(new ClanEvent(0,"Sample Boss of the Week","vorkath",now.minusSeconds(86400),now.plusSeconds(432000),
                java.util.Arrays.asList(new LeaderboardEntry("TRAP Raider",143),new LeaderboardEntry("TRAP Member",98),new LeaderboardEntry("TRAP Recruit",45))),"Sample data");
            skill.updateEvent(new ClanEvent(0,"Sample Skill of the Month","slayer",now.minusSeconds(86400),now.plusSeconds(1209600),
                java.util.Arrays.asList(new LeaderboardEntry("TRAP Leader",2400000),new LeaderboardEntry("TRAP Member",1600000),new LeaderboardEntry("TRAP Raider",950000))),"Sample data");
            TrapHouseDashboard panel=new TrapHouseDashboard(boss,skill,selections[i],ranking);ranking.showData(sample.deepCopy());
            previewActions(ranking,panel,sample);
            if(titles[i].equals("MEMBER PROFILE"))ranking.showMember(previewMember(sample,"TRAP Raider"));
            panel.setSize(width-12,height-55);layout(panel);
            if(selections[i]==4){eventTabs(panel).setSelectedIndex(i);layout(panel);}
            graphics.setColor(ClanStyle.GOLD);graphics.drawString(titles[i],i*width+8,40);
            Graphics2D page=(Graphics2D)graphics.create(i*width+5,48,width-10,height-50);panel.printAll(page);page.dispose();
        }
        graphics.dispose();
        try{ImageIO.write(image,"png",new File("build/trap-house-all-"+name+".png"));}catch(IOException error){throw new RuntimeException(error);}
    }
    private static void previewActions(RankingPanel ranking,TrapHouseDashboard dashboard,JsonObject sample) {
        ranking.itemArt(TrapHouseClanPreview::previewItem);
        ranking.pointsLogLoader((query,done)->done.accept(previewLog(sample,query)));
        ranking.actions(name->ranking.showMember(previewMember(sample,name)),(id,join)->{},dashboard::showProfile);
    }
    private static JsonObject previewLog(JsonObject sample,JsonObject query) {
        String member=query.get("player").getAsString();JsonObject profile=previewMember(sample,member).getAsJsonObject("profile");
        JsonArray entries=new JsonArray();long total=0;
        for(java.util.Map.Entry<String,JsonElement> source:profile.getAsJsonObject("breakdown").entrySet()) {
            if(!query.get("category").getAsString().equals("All")&&!query.get("category").getAsString().equals(source.getKey()))continue;
            long balance=source.getValue().getAsLong();if(balance==0)continue;
            // Explicit sample allocations sum to the fixture breakdown; these are never production awards.
            for(int i=0;i<8;i++) {
                boolean pet=source.getKey().equals("Pets"),item=pet||source.getKey().equals("Drops")||source.getKey().equals("Collection");
                int[] ids=pet?new int[]{12650,12651,12652,12653,12654,12655,12646,12921}:new int[]{4151,11802,11804,20997,22481,27277,13652,12924};
                String[] names=pet?new String[]{"Pet General Graardor","Pet Zilyana","Pet K'ril Tsutsaroth","Prince Black Dragon","Kalphite Princess","Pet Kraken","Baby mole","Pet snakeling"}:new String[]{"Abyssal whip","Armadyl godsword","Bandos godsword","Twisted bow","Sanguinesti staff (uncharged)","Tumeken's shadow (uncharged)","Dragon claws","Toxic blowpipe (empty)"};
                String title=item?names[i]:"Sample "+source.getKey()+" award "+(i+1);if(!title.toLowerCase(java.util.Locale.ROOT).contains(query.get("search").getAsString().toLowerCase(java.util.Locale.ROOT)))continue;
                long points=balance/8+(i==0?balance%8:0);JsonObject e=new JsonObject();e.addProperty("title",title);if(item)e.addProperty("itemId",ids[i]);e.addProperty("category",source.getKey());e.addProperty("points",points);e.addProperty("at","2026-10-25T12:00:00Z");e.addProperty("occurrences",1);entries.add(e);total+=points;
            }
        }
        java.util.List<JsonElement> sorted=new java.util.ArrayList<>();for(JsonElement e:entries)sorted.add(e);
        if(query.get("sort").getAsString().equals("oldest"))java.util.Collections.reverse(sorted);
        if(query.get("sort").getAsString().equals("highest"))sorted.sort((a,b)->Long.compare(b.getAsJsonObject().get("points").getAsLong(),a.getAsJsonObject().get("points").getAsLong()));
        int pages=Math.max(1,(sorted.size()+23)/24),page=Math.min(pages,query.get("page").getAsInt());JsonArray shown=new JsonArray();for(int i=(page-1)*24;i<Math.min(page*24,sorted.size());i++)shown.add(sorted.get(i));
        JsonObject data=new JsonObject();data.addProperty("player",member);data.addProperty("page",page);data.addProperty("pages",pages);data.addProperty("count",sorted.size());data.addProperty("total",total);data.add("entries",shown);return data;
    }
    private static void previewItem(JsonObject entry,java.util.function.BiConsumer<String,Icon> done) {
        if(!entry.has("itemId"))return;
        java.net.URL resource=TrapHouseClanPreview.class.getResource("/items/"+entry.get("itemId").getAsInt()+".png");
        if(resource!=null)done.accept(entry.get("title").getAsString(),new ImageIcon(resource));
    }
    private static JsonObject previewMember(JsonObject sample,String name) {
        JsonObject data=sample.deepCopy(),profile=data.getAsJsonObject("profile");
        if(name.equals(profile.get("name").getAsString()))return data;
        for(JsonElement entry:sample.getAsJsonArray("leaderboard")) {
            JsonObject member=entry.getAsJsonObject();if(!name.equals(member.get("name").getAsString()))continue;
            long points=member.get("points").getAsLong();profile.addProperty("name",name);profile.addProperty("points",points);profile.addProperty("lifetimeEarnedPoints",points);
            profile.addProperty("rank",member.get("rank").getAsString());profile.addProperty("automaticRank",member.get("rank").getAsString());
            profile.addProperty("balanceRank",member.get("rank").getAsString());profile.add("assignedRank",JsonNull.INSTANCE);profile.addProperty("automaticRankThreshold",0);
            profile.add("earnedRankHistory",new JsonArray());profile.add("metrics",new JsonObject());
            JsonObject breakdown=new JsonObject();breakdown.addProperty("Misc",points);profile.add("breakdown",breakdown);
            for(JsonElement goal:data.getAsJsonArray("goals")){goal.getAsJsonObject().addProperty("completed",false);goal.getAsJsonObject().addProperty("progress",0);}
            data.add("history",new JsonArray());data.remove("rewards");break;
        }
        return data;
    }
    private static JTabbedPane eventTabs(Container parent) {
        if(parent instanceof JTabbedPane)return (JTabbedPane)parent;
        for(Component child:parent.getComponents())if(child instanceof Container){JTabbedPane found=eventTabs((Container)child);if(found!=null)return found;}
        return null;
    }
    private static void rankPreview(JsonObject sample) {
        JPanel sheet=new JPanel(new GridLayout(1,2,12,0));sheet.setBackground(ClanStyle.BACKGROUND);
        sheet.setBorder(javax.swing.BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel progression=ClanStyle.column(),staff=ClanStyle.column();
        progression.add(ClanStyle.brand());progression.add(ClanStyle.label("PROGRESSION",14,ClanStyle.GOLD));
        for(JsonElement entry:sample.getAsJsonArray("ranks")) {
            JsonObject rank=entry.getAsJsonObject();String name=rank.get("name").getAsString();
            JPanel card=ClanStyle.card(null);card.add(RankEmblems.label(name,name,14,ClanStyle.GOLD));
            card.add(ClanStyle.text(rank.get("threshold").getAsLong()+" points"));ClanStyle.add(progression,card);
        }
        staff.add(ClanStyle.brand());staff.add(ClanStyle.label("MANAGEMENT / STAFF",14,ClanStyle.GOLD));
        for(JsonElement entry:sample.getAsJsonArray("protectedRanks")) {
            String name=entry.getAsJsonObject().get("name").getAsString();JPanel card=ClanStyle.card(null);
            card.add(RankEmblems.label(name,name,14,ClanStyle.GOLD));card.add(ClanStyle.text("Protected / Owner assignment"));ClanStyle.add(staff,card);
        }
        staff.add(ClanStyle.text("Staff titles are separate from service permissions.\n\nLifetime earnings and rank history are retained. Only the Owner may deduct points or authorize a demotion.\n\nIn-game rank changes remain manual."));
        sheet.add(progression);sheet.add(staff);sheet.setSize(640,1040);layout(sheet);
        BufferedImage image=new BufferedImage(sheet.getWidth(),sheet.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();sheet.printAll(graphics);graphics.dispose();
        try{ImageIO.write(image,"png",new File("build/trap-house-ranks-preview.png"));}catch(IOException error){throw new RuntimeException(error);}
    }
    private static void layout(Container panel){panel.doLayout();for(Component child:panel.getComponents())if(child instanceof Container)layout((Container)child);}
}
