package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.Locale;
import java.util.function.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Original server-backed clan screens. All mutations and navigation run on the EDT. */
final class RankingPanel extends JPanel {
    private final JPanel profile = ClanStyle.column(), ranks = ClanStyle.column(), leaders = ClanStyle.column();
    private final JPanel achievements = ClanStyle.column(), events = ClanStyle.column(), diary = ClanStyle.column(), races = ClanStyle.column();
    private final JPanel leaderRows = ClanStyle.column();
    private final JLabel status = ClanStyle.label("Ranking sync disabled",10,ClanStyle.MUTED);
    private final JProgressBar progress = new JProgressBar(0,100);
    private final JTextField search = new JTextField(), reason = new JTextField();
    private final JComboBox<String> manualRules = new JComboBox<>();
    private final JButton request;
    private final Runnable refresh;
    private Runnable discordLogin=()->{};
    void discordLogin(Runnable login){discordLogin=login;}
    void loginStatus(String text){status.setText(text);status.setToolTipText(text);}
    private JsonObject snapshot;
    private JsonArray leaderboard = new JsonArray();
    private Consumer<String> browse = name -> {};
    private BiConsumer<String,Boolean> registration = (id, join) -> {};
    private Runnable openProfile = () -> {};
    private Runnable openLeaderboard = () -> {};
    private final JScrollPane profileScroll=ClanStyle.scroll(profile);
    private JFrame fullBoard;
    private boolean viewingOther;
    private BiConsumer<JsonObject,Consumer<JsonObject>> logLoader=(query,done)->done.accept(null);
    private PointsLogPanel.ItemArt itemArt=(entry,done)->{};
    void itemArt(PointsLogPanel.ItemArt itemArt){this.itemArt=itemArt;}
    private final java.util.List<JDialog> logWindows=new java.util.ArrayList<>();
    private RankManagementPanel.Save rankEditor=(member,body,done)->done.accept("Rank editor is not connected.");
    private JDialog rankWindow;
    private RankManagementPanel rankForm;
    void rankEditor(RankManagementPanel.Save editor){rankEditor=editor;}
    private void openRankEditor() {
        if(snapshot==null||!snapshot.has("rankManagement")||!"OWNER".equals(string(snapshot.getAsJsonObject("profile"),"permission","")))return;
        if(rankWindow!=null){rankWindow.toFront();return;}
        rankForm=new RankManagementPanel(snapshot.getAsJsonObject("rankManagement"),array(snapshot,"ranks"),array(snapshot,"protectedRanks"),rankEditor);
        rankWindow=new JDialog(SwingUtilities.getWindowAncestor(this),"TRAP HOUSE - Owner rank management",Dialog.ModalityType.MODELESS);
        rankWindow.setContentPane(rankForm);rankWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        final JDialog dialog=rankWindow;final RankManagementPanel form=rankForm;
        rankWindow.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){form.close();if(rankWindow==dialog){rankForm=null;rankWindow=null;}}});
        rankWindow.pack();rankWindow.setLocationRelativeTo(this);rankWindow.setVisible(true);
    }
    private void closeRankEditor(){if(rankForm!=null)rankForm.close();if(rankWindow!=null)rankWindow.dispose();rankForm=null;rankWindow=null;}
    void pointsLogLoader(BiConsumer<JsonObject,Consumer<JsonObject>> loader){logLoader=loader;}
    private JButton pointsTile(String member,String category,long points) {
        JButton button=new JButton();button.setLayout(new BorderLayout());button.add(ProfileStyle.tile(abbreviated(points),category,ProfileStyle.ACCENT));
        button.setBackground(ProfileStyle.CARD);button.setBorder(BorderFactory.createEmptyBorder());button.setMargin(new Insets(0,0,0,0));button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("View "+member+"'s "+category+" points log");button.getAccessibleContext().setAccessibleName(category+" points log");
        button.addActionListener(e->{
            JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"TRAP HOUSE - "+member+"'s Points Log",Dialog.ModalityType.MODELESS);
            PointsLogPanel log=new PointsLogPanel(member,category,logLoader,itemArt);dialog.setContentPane(log);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent event){log.close();logWindows.remove(dialog);}});
            logWindows.add(dialog);dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);
        });return button;
    }

    RankingPanel(Runnable refresh) { this(refresh,(key,evidence)->{}); }
    RankingPanel(Runnable refresh, BiConsumer<String,String> reward) {
        super(new BorderLayout()); this.refresh = refresh;
        setBackground(ClanStyle.BACKGROUND);
        add(ClanStyle.button("Sign in with Discord",()->discordLogin.run()),BorderLayout.NORTH);
        add(profileScroll,BorderLayout.CENTER);
        status.setToolTipText("Rankings are calculated by the clan service. Promotions are manual.");
        add(status,BorderLayout.SOUTH);
        progress.setForeground(ClanStyle.GOLD); progress.setStringPainted(true);
        request = ClanStyle.button("Request manual reward",()-> {
            String evidence = reason.getText().trim();
            if (manualRules.getSelectedItem()==null || evidence.isEmpty() || evidence.length()>1000) {
                status.setText("Select a reward and enter a reason"); return;
            }
            reward.accept((String)manualRules.getSelectedItem(),evidence);
            status.setText("Request queued; awaiting server confirmation");
        });
        request.setEnabled(false);
        search.setToolTipText("Search all loaded clan members by name");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){renderLeaders();}
            public void removeUpdate(DocumentEvent e){renderLeaders();}
            public void changedUpdate(DocumentEvent e){renderLeaders();}
        });
        clear("Log in and connect to view your clan profile.");
    }

    void actions(Consumer<String> browse, BiConsumer<String,Boolean> registration, Runnable openProfile) {
        this.browse=browse; this.registration=registration; this.openProfile=openProfile;
    }
    void navigation(Runnable openProfile,Runnable openLeaderboard) {
        this.openProfile=openProfile;this.openLeaderboard=openLeaderboard;
    }
    void addTabs(JTabbedPane tabs) {
        tabs.addTab("Profile",this); tabs.addTab("Ranks",ClanStyle.scroll(ranks));
        tabs.addTab("Leaderboard",ClanStyle.scroll(leaders)); tabs.addTab("Achievements",ClanStyle.scroll(achievements));
        tabs.addTab("Events",ClanStyle.scroll(events)); tabs.addTab("Diary",ClanStyle.scroll(diary));
    }
    JComponent[] pages() { return new JComponent[]{this,ClanStyle.scroll(ranks),ClanStyle.scroll(leaders),ClanStyle.scroll(achievements),ClanStyle.scroll(events),ClanStyle.scroll(diary)}; }
    JComponent racePage() { return ClanStyle.scroll(races); }

    static JsonArray array(JsonObject data,String key) { return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray(); }
    private static String string(JsonObject data,String key,String fallback) { return data.has(key)&&!data.get(key).isJsonNull()?data.get(key).getAsString():fallback; }
    private static long number(JsonObject data,String key) { return data.has(key)&&!data.get(key).isJsonNull()?data.get(key).getAsLong():0; }
    private static String formatted(long value) { return String.format(Locale.US,"%,d",value); }
    private void message(JPanel page,String title,String text) {
        page.removeAll(); JPanel card=ClanStyle.card(title); card.add(ClanStyle.text(text)); ClanStyle.add(page,card); ClanStyle.finish(page);
    }
    private void toolbar(JPanel page,String title) {
        page.add(ClanStyle.label(title,12,ClanStyle.GOLD));
        ClanStyle.add(page,ClanStyle.button("Refresh",refresh));
    }

    void showData(JsonObject data) {
        if(!"OWNER".equals(string(data.getAsJsonObject("profile"),"permission","")))closeRankEditor();
        snapshot=data;
        if(!viewingOther)renderProfile(data,false); renderRanks(data);
        leaderboard=array(data,"leaderboard"); renderLeaderboardPage();
        renderGoals(achievements,data,"Achievements",false);
        renderGoals(diary,data,"Clan diary",true);
        renderEvents(data); renderRaces(data);
        status.setText("Synced with clan server");
    }

    void showMember(JsonObject data) { viewingOther=true; renderProfile(data,true); openProfile.run(); profileScroll.getVerticalScrollBar().setValue(0);status.setText("Viewing "+string(data.getAsJsonObject("profile"),"name","member")+"'s profile"); }

    private void renderProfile(JsonObject data,boolean other) {
        profile.removeAll();profile.setBackground(ProfileStyle.BACKGROUND);
        profileScroll.getViewport().setBackground(ProfileStyle.BACKGROUND);
        profile.setBorder(javax.swing.BorderFactory.createEmptyBorder(4,2,8,2));
        JsonObject player=data.getAsJsonObject("profile");long points=number(player,"points");
        String name=string(player,"name","Member"),automatic=string(player,"automaticRank",string(player,"rank","Unranked"));
        JPanel breadcrumb=ProfileStyle.row();
        if(other) {
            JButton back=ClanStyle.button("< Back",()-> {
                viewingOther=false;if(snapshot!=null)renderProfile(snapshot,false);openLeaderboard.run();
                status.setText("Clan leaderboard");
            });
            back.setBackground(ProfileStyle.CARD);breadcrumb.add(back,BorderLayout.WEST);
        } else breadcrumb.add(ProfileStyle.label("Profile",11,ProfileStyle.MUTED),BorderLayout.WEST);
        breadcrumb.add(ProfileStyle.label(name,11,ProfileStyle.ACCENT),BorderLayout.EAST);ClanStyle.add(profile,breadcrumb);

        JPanel hero=ProfileStyle.card(null),identity=ProfileStyle.row();
        identity.add(ProfileStyle.rank(name,string(player,"rank",automatic),ProfileStyle.TEXT),BorderLayout.CENTER);
        JPanel total=new JPanel(new GridLayout(0,1));total.setOpaque(false);
        JLabel amount=ProfileStyle.label(formatted(points),14,ProfileStyle.ACCENT),caption=ProfileStyle.label("TRAP points",9,ProfileStyle.MUTED);
        amount.setHorizontalAlignment(SwingConstants.RIGHT);caption.setHorizontalAlignment(SwingConstants.RIGHT);
        total.add(amount);total.add(caption);identity.add(total,BorderLayout.EAST);hero.add(identity);
        String displayed=string(player,"rank",automatic);
        hero.add(ProfileStyle.rank("Rank: "+displayed,displayed,ProfileStyle.ACCENT));
        long lower=0,upper=points;boolean next=false;String nextRank="";
        long earnedThreshold=player.has("automaticRankThreshold")?number(player,"automaticRankThreshold"):-1;
        for(JsonElement element:array(data,"ranks")) {
            JsonObject tier=element.getAsJsonObject();long threshold=number(tier,"threshold");
            if(threshold<=Math.max(points,earnedThreshold))lower=threshold;
            else {upper=threshold;next=true;nextRank=string(tier,"name","");break;}
        }
        JPanel nextRow=ProfileStyle.row();
        nextRow.add(ProfileStyle.rank(next?formatted(upper-points)+" pts to "+nextRank:"Highest earned tier",nextRank,ProfileStyle.ACCENT),BorderLayout.CENTER);
        nextRow.add(ProfileStyle.label("\u2192",15,ProfileStyle.MUTED),BorderLayout.EAST);hero.add(nextRow);
        progress.setStringPainted(false);progress.setBorderPainted(false);progress.setForeground(ProfileStyle.ACCENT);
        progress.setBackground(ProfileStyle.BACKGROUND);progress.setPreferredSize(new Dimension(180,5));
        progress.setValue(next?(int)Math.max(0,Math.min(100,100.0*(points-lower)/(upper-lower))):100);
        progress.setToolTipText(next?formatted(upper-points)+" pts to "+nextRank:"Maximum earned rank reached");hero.add(progress);ClanStyle.add(profile,hero);

        ClanStyle.add(profile,ProfileStyle.label("Points from:",12,ProfileStyle.ACCENT));
        JsonObject breakdown=player.has("breakdown")?player.getAsJsonObject("breakdown"):new JsonObject();
        JPanel tiles=new JPanel(new GridLayout(0,3,3,3));tiles.setOpaque(false);
        for(String category:new String[]{"Drops","Pets","Milestones","Diaries","Challenges","Events","Combat","Collection","Efficiency"}) {
            if((category.equals("Combat")||category.equals("Collection")||category.equals("Efficiency"))&&!breakdown.has(category))continue;
            JButton tile=pointsTile(name,category,number(breakdown,category));
            tile.setToolTipText(category+": "+formatted(number(breakdown,category))+" points");tiles.add(tile);
        }
        ClanStyle.add(profile,tiles);ClanStyle.add(profile,pointsTile(name,"Misc",number(breakdown,"Misc")));
        JsonObject metrics=player.has("metrics")?player.getAsJsonObject("metrics"):new JsonObject();
        JPanel efficiency=new JPanel(new GridLayout(1,2,4,0));efficiency.setOpaque(false);
        efficiency.add(ProfileStyle.tile(metric(metrics,"ehp"),"EHP",ProfileStyle.GREEN));efficiency.add(ProfileStyle.tile(metric(metrics,"ehb"),"EHB",ProfileStyle.GREEN));
        long weeklyCap=metrics.has("weeklyCap")?number(metrics,"weeklyCap"):100;
        efficiency.setToolTipText("Wise Old Man: "+(metrics.has("ehpRate")?number(metrics,"ehpRate"):5)+" pts/EHP gained, "+(metrics.has("ehbRate")?number(metrics,"ehbRate"):10)+" pts/EHB gained; "+weeklyCap+" pts/week maximum. "
            +(metrics.has("baselineAt")?"This week: "+number(metrics,"weeklyPoints")+" / "+weeklyCap+". ":"Waiting for a fresh starting baseline. ")
            +(metrics.has("fetchedAt")?"Last fetched: "+string(metrics,"fetchedAt",""):"Not imported yet."));ClanStyle.add(profile,efficiency);
        compactProfileGoals(data);
        profileRankDetails(player,automatic);
        JPanel history=ProfileStyle.card("RECENT AWARDS");
        JsonArray awards=array(data,"history");
        if(awards.size()==0) history.add(ClanStyle.text("No confirmed awards yet."));
        for(JsonElement element:awards) {
            JsonObject award=element.getAsJsonObject();
            history.add(ClanStyle.text((number(award,"points")>0?"+":"")+number(award,"points")+" / "+string(award,"key","")+"\n"+string(award,"at","")));
        }
        ClanStyle.add(profile,history);
        if(!other) {
            if("OWNER".equals(string(player,"permission",""))&&data.has("rankManagement"))
                ClanStyle.add(profile,ClanStyle.button("Manage player ranks",this::openRankEditor));
            JPanel manual=ProfileStyle.card("MANUAL REWARDS"); manual.add(ClanStyle.text("Staff approval is required. Select a reward and provide evidence."));
            Object selected=manualRules.getSelectedItem(); manualRules.removeAllItems();
            for(JsonElement element:array(data,"sources")) {
                JsonObject source=element.getAsJsonObject(); if("MANUAL".equals(string(source,"type",""))) manualRules.addItem(string(source,"key",""));
            }
            if(selected!=null)manualRules.setSelectedItem(selected);
            request.setEnabled(manualRules.getItemCount()>0);
            manual.add(manualRules); manual.add(reason); manual.add(request);
            for(JsonElement element:array(data,"rewards")) {
                JsonObject row=element.getAsJsonObject(); manual.add(ClanStyle.text(string(row,"key","")+" / "+string(row,"status","")));
            }
            ClanStyle.add(profile,manual); ClanStyle.add(profile,ClanStyle.button("Refresh rankings",refresh));
        }
        ClanStyle.finish(profile);
    }

    private static String abbreviated(long value) {
        long magnitude=Math.abs(value);
        if(magnitude>=1000000)return String.format(Locale.US,"%.1fM",value/1000000.0);
        if(magnitude>=1000)return String.format(Locale.US,"%.1fK",value/1000.0);
        return Long.toString(value);
    }
    private static String metric(JsonObject metrics,String key) {
        if(!metrics.has(key)||metrics.get(key).isJsonNull())return "Not synced";
        try{return String.format(Locale.US,"%,.1f",metrics.get(key).getAsDouble());}catch(RuntimeException invalid){return "Not synced";}
    }
    private void compactProfileGoals(JsonObject data) {
        java.util.Map<String,JPanel> groups=new java.util.LinkedHashMap<>();
        for(JsonElement entry:array(data,"goals")) {
            JsonObject goal=entry.getAsJsonObject();String category=string(goal,"category","");
            if(!category.equals("Milestones")&&!category.equals("Combat"))continue;
            String section=string(goal,"section",category);
            JPanel group=groups.computeIfAbsent(section,ProfileStyle::card),row=ProfileStyle.row();
            boolean complete=goal.has("completed")&&goal.get("completed").getAsBoolean();
            JLabel check=ProfileStyle.label(complete?"\u2713":" ",11,ProfileStyle.GREEN);check.setOpaque(true);
            check.setHorizontalAlignment(SwingConstants.CENTER);check.setBackground(complete?new Color(38,66,51):new Color(43,47,57));
            check.setPreferredSize(new Dimension(12,12));check.setToolTipText(complete?"Completed":"Not completed");
            JPanel marker=new JPanel(new GridBagLayout());marker.setOpaque(false);marker.add(check);row.add(marker,BorderLayout.WEST);
            JLabel title=ProfileStyle.label(string(goal,"name","Goal"),10,ProfileStyle.TEXT);
            title.setPreferredSize(new Dimension(95,18));title.setMinimumSize(new Dimension(0,18));row.add(title,BorderLayout.CENTER);
            row.add(ProfileStyle.label(number(goal,"points")==0?"Tracking":"+"+number(goal,"points")+" pts",10,ProfileStyle.ACCENT),BorderLayout.EAST);
            String tooltip=string(goal,"name","")+" / "+string(goal,"description","")+" / "+number(goal,"progress")+" of "+number(goal,"target");
            title.setToolTipText(tooltip);row.setToolTipText(tooltip);group.add(row);
        }
        for(JPanel group:groups.values())ClanStyle.add(profile,group);
    }
    private void profileRankDetails(JsonObject player,String automatic) {
        JPanel details=ProfileStyle.card(null);details.setVisible(false);
        details.add(ProfileStyle.rank("Automatic: "+automatic,automatic,ProfileStyle.ACCENT));
        details.add(ClanStyle.text("Rank from current balance: "+string(player,"balanceRank",automatic)));
        String assigned=string(player,"assignedRank","None");
        details.add(ProfileStyle.rank("Assigned: "+assigned+" / "+(player.has("rankLocked")&&player.get("rankLocked").getAsBoolean()?"Locked":"Unlocked"),assigned,ProfileStyle.TEXT));
        details.add(ClanStyle.text("Promotion eligible: "+(player.has("promotionEligible")&&player.get("promotionEligible").getAsBoolean()?"Yes - staff review required":"No")));
        if(player.has("lifetimeEarnedPoints"))details.add(ClanStyle.text("Lifetime earned: "+formatted(number(player,"lifetimeEarnedPoints"))+" / current balance: "+formatted(number(player,"points"))));
        JsonArray earnedHistory=array(player,"earnedRankHistory");
        if(earnedHistory.size()>0) {
            details.add(ProfileStyle.label("EARNED RANK HISTORY",11,ProfileStyle.TEXT));
            for(JsonElement entry:earnedHistory) {
                JsonObject tier=entry.getAsJsonObject();details.add(ProfileStyle.rank(string(tier,"rank",""),string(tier,"rank",""),ProfileStyle.ACCENT));
                details.add(ClanStyle.text(formatted(number(tier,"threshold"))+" points / "+string(tier,"at","")));
            }
        }
        details.add(ClanStyle.text("Earned ranks survive deductions. Only the Owner may authorize a demotion. In-game promotions remain manual."));
        JToggleButton toggle=new JToggleButton("Rank details & history");toggle.setBackground(ProfileStyle.CARD);toggle.setForeground(ProfileStyle.ACCENT);
        toggle.addActionListener(event->{details.setVisible(toggle.isSelected());ClanStyle.finish(profile);profileScroll.revalidate();});
        ClanStyle.add(profile,toggle);ClanStyle.add(profile,details);
    }
    private void renderRanks(JsonObject data) {
        ranks.removeAll(); toolbar(ranks,"RANKS & POINTS");
        for(JsonElement element:array(data,"ranks")) {
            JsonObject row=element.getAsJsonObject(); JPanel card=ClanStyle.card(null);
            card.add(RankEmblems.label(string(row,"name","Rank"),string(row,"name","Rank"),14,ClanStyle.GOLD));
            card.add(ClanStyle.label(formatted(number(row,"threshold"))+" points",16,ClanStyle.WHITE)); ClanStyle.add(ranks,card);
        }
        if(array(data,"protectedRanks").size()>0) {
            JPanel staff=ClanStyle.card("MANAGEMENT / STAFF");
            for(JsonElement element:array(data,"protectedRanks")) {
                String rank=string(element.getAsJsonObject(),"name","");
                staff.add(RankEmblems.label(rank,rank,14,ClanStyle.GOLD));
            }
            staff.add(ClanStyle.text("Protected ranks. Assigned only by the Owner; never earned through points. Rank titles do not grant service permissions."));
            ClanStyle.add(ranks,staff);
        }
        JPanel sources=ClanStyle.card("WAYS TO EARN POINTS");
        for(JsonElement element:array(data,"sources")) {
            JsonObject source=element.getAsJsonObject();
            sources.add(ClanStyle.text(string(source,"name",string(source,"key",""))+"\n+"+number(source,"points")+" pts / "+string(source,"type","")+" / daily cap "+number(source,"dailyLimit")));
        }
        if(array(data,"sources").size()==0)sources.add(ClanStyle.text("Staff have not enabled scoring rules yet."));
        ClanStyle.add(ranks,sources); ClanStyle.finish(ranks);
    }

    private void renderLeaderboardPage() {
        leaders.removeAll(); toolbar(leaders,"CLAN LEADERBOARD");
        ClanStyle.add(leaders,ClanStyle.label(leaderboard.size()+" members",10,ClanStyle.MUTED));
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); ClanStyle.add(leaders,search);
        ClanStyle.add(leaders,ClanStyle.button("Open full leaderboard",this::openFullBoard));
        ClanStyle.add(leaders,leaderRows); renderLeaders();
    }
    private void renderLeaders() {
        leaderRows.removeAll(); int position=0,visible=0;
        String filter=search.getText().trim().toLowerCase(Locale.ROOT);
        for(JsonElement element:leaderboard) {
            JsonObject row=element.getAsJsonObject(); position++; String name=string(row,"name","");
            if(!name.toLowerCase(Locale.ROOT).contains(filter))continue;
            final String member=name;
            JButton button=ClanStyle.button("#"+position+"  "+name+"   "+formatted(number(row,"points")),()->browseMember(member));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText("View "+name+" / "+string(row,"rank","Unranked"));
            button.setForeground(position<=3?ClanStyle.GOLD:ClanStyle.WHITE); ClanStyle.add(leaderRows,button);
            ClanStyle.add(leaderRows,RankEmblems.label(string(row,"rank","Unranked"),string(row,"rank","Unranked"),10,ClanStyle.MUTED)); visible++;
        }
        if(visible==0)leaderRows.add(ClanStyle.text("No matching clan members.")); ClanStyle.finish(leaderRows); ClanStyle.finish(leaders);
    }
    private void openFullBoard() {
        if(GraphicsEnvironment.isHeadless())return;
        if(fullBoard!=null) fullBoard.dispose();
        fullBoard=new JFrame("TRAP HOUSE / Clan leaderboard");
        String[] columns={"Rank","Member","Points","Eligible rank"}; Object[][] rows=new Object[leaderboard.size()][4];
        for(int i=0;i<rows.length;i++) {
            JsonObject row=leaderboard.get(i).getAsJsonObject(); rows[i]=new Object[]{i+1,string(row,"name",""),number(row,"points"),string(row,"rank","")};
        }
        JTable table=new JTable(new javax.swing.table.DefaultTableModel(rows,columns){public boolean isCellEditable(int r,int c){return false;}public Class<?> getColumnClass(int c){return c==0?Integer.class:c==2?Long.class:String.class;}});
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(3).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable owner,Object value,boolean selected,boolean focus,int row,int column){
                JLabel label=(JLabel)super.getTableCellRendererComponent(owner,value,selected,focus,row,column);
                label.setIcon(RankEmblems.icon(String.valueOf(value)));label.setIconTextGap(5);return label;
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent e){int row=table.rowAtPoint(e.getPoint());if(e.getClickCount()==1&&row>=0)browseMember((String)table.getValueAt(row,1));}});
        JTextField filter=new JTextField(); filter.setToolTipText("Search members");
        javax.swing.table.TableRowSorter<?> sorter=(javax.swing.table.TableRowSorter<?>)table.getRowSorter();
        filter.getDocument().addDocumentListener(new DocumentListener(){private void apply(){sorter.setRowFilter(RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(filter.getText()),1));}public void insertUpdate(DocumentEvent e){apply();}public void removeUpdate(DocumentEvent e){apply();}public void changedUpdate(DocumentEvent e){apply();}});
        fullBoard.add(filter,BorderLayout.NORTH); fullBoard.add(new JScrollPane(table)); fullBoard.setSize(620,600);
        fullBoard.setLocationRelativeTo(this); fullBoard.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); fullBoard.setVisible(true);
    }
    private void browseMember(String member) {
        if(fullBoard!=null){fullBoard.dispose();fullBoard=null;}
        status.setText("Loading "+member+"'s profile...");browse.accept(member);
    }

    private void renderGoals(JPanel page,JsonObject data,String title,boolean diaries) {
        page.removeAll(); toolbar(page,title.toUpperCase(Locale.ROOT)); addGoalGroups(page,data,diaries,false);
        ClanStyle.finish(page);
    }
    private void addGoalGroups(JPanel page,JsonObject data,boolean diaries,boolean summary) {
        java.util.Map<String,JPanel> groups=new java.util.LinkedHashMap<>(); int count=0;
        for(JsonElement element:array(data,"goals")) {
            JsonObject goal=element.getAsJsonObject(); String category=string(goal,"category","Milestones");
            if(diaries!=category.equals("Diaries"))continue;
            if(summary&&!category.equals("Milestones")&&!category.equals("Combat"))continue;
            JPanel group=groups.computeIfAbsent(string(goal,"section",category),ClanStyle::card);
            boolean completed=goal.has("completed")&&goal.get("completed").getAsBoolean();
            group.add(ClanStyle.label((completed?"[x] ":"[ ] ")+string(goal,"name","Goal"),11,completed?ClanStyle.GREEN:ClanStyle.WHITE));
            group.add(ClanStyle.text(string(goal,"description","")+"\n"+number(goal,"progress")+" / "+number(goal,"target")+"   "+(number(goal,"points")==0?"Tracking only":"+"+number(goal,"points")+" pts")));
            if(goal.has("claimKey")&&!goal.get("claimKey").isJsonNull()&&!completed&&(!summary||!viewingOther)) {
                String key=goal.get("claimKey").getAsString();
                group.add(ClanStyle.button("Request staff verification",()->{manualRules.setSelectedItem(key);reason.setText("Verify: "+string(goal,"name",""));openProfile.run();status.setText("Add evidence in Manual rewards and submit");}));
            }
            count++;
        }
        for(JPanel group:groups.values())ClanStyle.add(page,group);
        if(count==0&&!summary)ClanStyle.add(page,ClanStyle.text(diaries?"No clan diary tasks configured yet.":"No achievement definitions configured yet. Live supported unlocks still track enabled scoring rules."));
    }

    private void renderEvents(JsonObject data) {
        events.removeAll(); toolbar(events,"CLAN EVENTS");
        for(JsonElement element:array(data,"clanEvents")) {
            JsonObject row=element.getAsJsonObject(); JPanel card=ClanStyle.card(string(row,"title","Event"));
            card.add(ClanStyle.text(string(row,"description","")+"\n"+string(row,"startsAt","")+"\n"+number(row,"registrations")+" registered"));
            boolean joined=row.has("registered")&&row.get("registered").getAsBoolean();
            String id=string(row,"id",""); JButton join=ClanStyle.button(joined?"Cancel registration":"Register",()->registration.accept(id,!joined));
            join.setEnabled(row.has("registrationOpen")&&row.get("registrationOpen").getAsBoolean());
            card.add(join); ClanStyle.add(events,card);
        }
        if(array(data,"clanEvents").size()==0)ClanStyle.add(events,ClanStyle.text("No upcoming clan events. Staff can publish events through the clan service.")); ClanStyle.finish(events);
    }
    private void renderRaces(JsonObject data) {
        races.removeAll(); toolbar(races,"RACE COMPETITIONS");
        for(JsonElement element:array(data,"races")) {
            JsonObject race=element.getAsJsonObject(); JPanel card=ClanStyle.card(string(race,"title","Race"));
            card.add(ClanStyle.text(string(race,"description","")+"\n"+string(race,"startsAt","")+" - "+string(race,"endsAt","")));
            for(JsonElement award:array(race,"firsts")) {
                JsonObject row=award.getAsJsonObject(); card.add(ClanStyle.label(string(row,"label","")+" / +"+number(row,"points")+" pts",11,ClanStyle.GOLD));
                card.add(ClanStyle.text(string(row,"winner","Unclaimed")));
            }
            int place=0; for(JsonElement entry:array(race,"standings")) {
                JsonObject row=entry.getAsJsonObject(); card.add(ClanStyle.text("#"+(++place)+" "+string(row,"name","")+"   "+number(row,"score")+" / +"+number(row,"projectedPoints")+" pts projected"));
            }
            card.add(ClanStyle.text("Placement rewards are projected. Staff approve final rewards.")); ClanStyle.add(races,card);
        }
        if(array(data,"races").size()==0)ClanStyle.add(races,ClanStyle.text("No race competitions configured. Boss and Skill competitions use Wise Old Man."));ClanStyle.finish(races);
    }
    void clear(String message) {
        closeRankEditor();
        for(JDialog dialog:new java.util.ArrayList<>(logWindows)){((PointsLogPanel)dialog.getContentPane()).close();dialog.dispose();}logWindows.clear();
        snapshot=null; viewingOther=false;leaderboard=new JsonArray();
        message(profile,"YOUR PROFILE",message);message(ranks,"RANKS & POINTS","Connect to load clan rank requirements and point sources.");
        message(achievements,"ACHIEVEMENTS",message);message(diary,"CLAN DIARY",message);
        message(events,"CLAN EVENTS",message);message(races,"RACE COMPETITIONS",message);renderLeaderboardPage();
        manualRules.removeAllItems();request.setEnabled(false);reason.setText("");progress.setValue(0);status.setText(message);
        if(fullBoard!=null){fullBoard.dispose();fullBoard=null;}
    }
    void error(){status.setText("Sync failed; displayed data may be stale");}
    void actionError(String message){status.setText(message);status.setToolTipText(message);}
}
