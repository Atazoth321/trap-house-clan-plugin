package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import static com.traphouse.BrowserStyle.*;

/** Native clan browser with guarded asynchronous selections and a bounded session cache. */
final class FullLeaderboardPanel extends JPanel {
    interface Loader {void load(JsonObject selection,boolean refresh,Consumer<JsonObject> success,Consumer<String> failure);}
    private static final String[][] MODES={{"clog","Collection log"},{"luck","Luck"},{"deserved","Deserved"},{"dryboard","Dryboard"},{"leaderboard","Leaderboards"}};
    private static final String[][] METRICS={{"clog","Collection log"},{"pets","Pets"},{"points","Points"},{"greenlogs","Greenlogs"},{"xp","Total XP"},{"monthly-clog","Monthly clog"},{"monthly-points","Monthly points"},{"clues","Clues"},{"combat","Combat points"},{"bosses","Boss kills"}};
    private final Loader loader;
    private final PointsLogPanel.ItemArt art;
    private final JTextField search=new JTextField();
    private final JLabel title=label("Clan Points Leaderboard",32,GOLD),caption=label("Full clan standings",23,INK),updated=label("",14,INK),notice=label("",15,INK);
    private final JPanel statistics=panel(new GridLayout(2,5,7,7)),choices=panel(new BorderLayout()),body=panel(new BorderLayout(10,0)),sidebar=panel(new GridLayout(0,1,0,4)),groupTabs=panel(new GridLayout(1,5,7,0));
    private final JComboBox<Choice> categoryPicker=new JComboBox<>();
    private final Map<String,JButton> modeButtons=new LinkedHashMap<>(),metricButtons=new LinkedHashMap<>(),sectionButtons=new LinkedHashMap<>();
    private final Map<String,JsonObject> cache=new LinkedHashMap<>(16,.75f,true),categories=new LinkedHashMap<>();
    private final Set<String> completedPages=new HashSet<>();
    private String mode="leaderboard",metric="points",category,section="Bosses",player,viewer;
    private boolean member,active=true,catalogueLoading,adjusting;
    private long revision;
    private JsonObject current;
    private String displayedSelection;
    private BrowserStandings standings;
    private final JPopupMenu suggestions=new JPopupMenu();
    private static final class Choice {final String key,label;Choice(String key,String label){this.key=key;this.label=label;}@Override public String toString(){return label;}}

    FullLeaderboardPanel(Loader loader,String viewer,boolean member,PointsLogPanel.ItemArt art) {
        super(new BorderLayout(0,10));this.loader=loader;this.viewer=viewer;this.member=member;this.player=viewer;this.art=art;
        setBackground(BG);setBorder(new EmptyBorder(10,12,12,12));setPreferredSize(new Dimension(1150,770));
        JPanel top=panel(new BorderLayout(0,9));title.setHorizontalAlignment(SwingConstants.CENTER);title.setOpaque(true);title.setBackground(CARD);title.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(16,12,16,12)));JPanel banner=new TrapHousePanel.Card(true);banner.setLayout(new BorderLayout(20,0));banner.setBorder(new EmptyBorder(12,14,12,14));banner.add(ClanStyle.brand(),BorderLayout.WEST);title.setOpaque(false);title.setBorder(new EmptyBorder(8,12,8,12));title.setHorizontalAlignment(SwingConstants.RIGHT);banner.add(title,BorderLayout.CENTER);top.add(banner,BorderLayout.NORTH);
        JPanel controls=panel(null);controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));JPanel modes=panel(new GridLayout(1,5,7,0));
        for(String[] entry:MODES){JButton button=button(entry[1],()->chooseMode(entry[0]));button.getAccessibleContext().setAccessibleName("mode:"+entry[0]);modeButtons.put(entry[0],button);modes.add(button);}controls.add(modes);controls.add(Box.createVerticalStrut(9));
        JPanel searchRow=panel(new BorderLayout(8,0));search.setFont(font(18));search.setBackground(CARD);search.setForeground(INK);search.setCaretColor(INK);search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(5,8,5,8)));search.setToolTipText("Search standings. In Collection log, enter a character name and press Enter.");search.getAccessibleContext().setAccessibleName("Find a clan member");search.putClientProperty("JTextField.placeholderText","Find a clan member...");searchRow.add(search,BorderLayout.CENTER);
        JPanel actions=panel(new GridLayout(1,3,7,0));actions.add(button("Find me",this::findMe));actions.add(button("Refresh",()->reload(true)));actions.add(button("?",this::help));searchRow.add(actions,BorderLayout.EAST);controls.add(searchRow);controls.add(Box.createVerticalStrut(9));
        for(String[] entry:METRICS){JButton button=button(entry[1],()->{metric=entry[0];category=null;current=null;configure();reload(false);});button.getAccessibleContext().setAccessibleName("metric:"+entry[0]);metricButtons.put(entry[0],button);statistics.add(button);}controls.add(statistics);controls.add(choices);top.add(controls,BorderLayout.CENTER);add(top,BorderLayout.NORTH);
        JPanel content=panel(new BorderLayout(0,9)),heading=panel(new BorderLayout());heading.add(caption,BorderLayout.WEST);heading.add(updated,BorderLayout.EAST);content.add(heading,BorderLayout.NORTH);content.add(body,BorderLayout.CENTER);add(content,BorderLayout.CENTER);add(notice,BorderLayout.SOUTH);
        for(String group:new String[]{"Bosses","Raids","Clues","Minigames","Other"}){JButton button=button(group,()->{section=group;category=null;configure();reload(false);});sectionButtons.put(group,button);groupTabs.add(button);}
        categoryPicker.setBackground(CARD);categoryPicker.setForeground(INK);categoryPicker.setEditable(true);categoryPicker.setFont(font(18));categoryPicker.setToolTipText("Choose or type a category; press Enter");
        categoryPicker.addActionListener(event->{if(adjusting)return;Object selected=categoryPicker.getSelectedItem();String key=null;if(selected instanceof Choice)key=((Choice)selected).key;else if(selected!=null){String query=selected.toString().trim();for(int i=0;i<categoryPicker.getItemCount();i++){Choice choice=categoryPicker.getItemAt(i);if(query.equalsIgnoreCase(choice.label)||query.equalsIgnoreCase(choice.key)){key=choice.key;break;}}}if(key!=null){suggestions.setVisible(false);category=key;reload(false);}});
        JTextField categoryText=(JTextField)categoryPicker.getEditor().getEditorComponent();categoryText.setBackground(CARD);categoryText.setForeground(INK);categoryText.setCaretColor(INK);categoryText.setFont(font(18));
        categoryText.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent event){suggest();}public void removeUpdate(DocumentEvent event){suggest();}public void changedUpdate(DocumentEvent event){suggest();}});
        search.addActionListener(event->{if(mode.equals("clog")){String name=search.getText().trim();if(name.matches("[A-Za-z0-9 _-]{1,12}")){player=name;reload(false);}else notice.setText("Enter an OSRS character name, then press Enter.");}});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});configure();
    }
    void start(){reload(false);}
    void identity(String viewer,boolean member){this.viewer=viewer;this.member=member;}
    void close(){active=false;revision++;cache.clear();suggestions.setVisible(false);}
    void error(){updated.setText("Refresh failed · showing previous standings");}
    private void chooseMode(String value){mode=value;current=null;completedPages.clear();category=!mode.equals("leaderboard")&&categories.containsKey("vorkath")?"vorkath":null;configure();reload(false);}
    private void configure() {
        title.setText(mode.equals("leaderboard")?metric.equals("points")?"Clan Points Leaderboard":"Clan "+metricLabel()+" Leaderboard":mode.equals("clog")?"Clan Collection Log":mode.equals("luck")?"Collection Log Luck":mode.equals("deserved")?"Deserved Greenlogs":"Dryboard");
        for(Map.Entry<String,JButton> entry:modeButtons.entrySet())select(entry.getValue(),entry.getKey().equals(mode));for(Map.Entry<String,JButton> entry:metricButtons.entrySet())select(entry.getValue(),entry.getKey().equals(metric));statistics.setVisible(mode.equals("leaderboard"));choices.removeAll();sidebar.removeAll();
        if(!mode.equals("leaderboard")&&!mode.equals("dryboard")) {
            choices.setBorder(new EmptyBorder(9,0,0,0));choices.add(groupTabs,BorderLayout.NORTH);for(Map.Entry<String,JButton> entry:sectionButtons.entrySet())select(entry.getValue(),entry.getKey().equals(section));
            List<Map.Entry<String,JsonObject>> matching=new ArrayList<>();for(Map.Entry<String,JsonObject> entry:categories.entrySet())if(section.equals(RankingPanel.string(entry.getValue(),"section","Other")))matching.add(entry);matching.sort(Comparator.comparing(entry->RankingPanel.string(entry.getValue(),"label","")));
            boolean present=false;for(Map.Entry<String,JsonObject> entry:matching)if(entry.getKey().equals(category))present=true;if(!present)category=matching.isEmpty()?null:matching.get(0).getKey();
            for(Map.Entry<String,JsonObject> entry:matching){JButton button=button(RankingPanel.string(entry.getValue(),"label",entry.getKey()),()->{category=entry.getKey();configure();reload(false);});select(button,entry.getKey().equals(category));if(completedPages.contains(entry.getKey()))button.setForeground(GREEN);sidebar.add(button);}
        } else if(mode.equals("dryboard")) {
            choices.setBorder(new EmptyBorder(9,0,0,0));adjusting=true;categoryPicker.removeAllItems();Choice selected=null;
            for(Map.Entry<String,JsonObject> entry:categories.entrySet()){Choice choice=new Choice(entry.getKey(),RankingPanel.string(entry.getValue(),"label",entry.getKey()));categoryPicker.addItem(choice);if(entry.getKey().equals(category))selected=choice;}
            if(selected!=null)categoryPicker.setSelectedItem(selected);else if(categoryPicker.getItemCount()>0){categoryPicker.setSelectedIndex(0);category=categoryPicker.getItemAt(0).key;}adjusting=false;choices.add(categoryPicker,BorderLayout.CENTER);
        } else if(metric.equals("clues")) {
            choices.setBorder(new EmptyBorder(9,0,0,0));JPanel tiers=panel(new GridLayout(1,7,5,0));for(String tier:new String[]{"all","beginner","easy","medium","hard","elite","master"}){JButton button=button(tier.equals("all")?"All tiers":pretty(tier),()->{category=tier;configure();reload(false);});select(button,tier.equals(category==null?"all":category));tiers.add(button);}choices.add(tiers);
        } else if(metric.equals("bosses")) {
            choices.setBorder(new EmptyBorder(9,0,0,0));adjusting=true;categoryPicker.removeAllItems();categoryPicker.addItem(new Choice("all","All bosses"));if(current!=null)for(JsonElement value:RankingPanel.array(current,"choices"))categoryPicker.addItem(new Choice(value.getAsString(),pretty(value.getAsString())));for(int i=0;i<categoryPicker.getItemCount();i++)if(categoryPicker.getItemAt(i).key.equals(category))categoryPicker.setSelectedIndex(i);adjusting=false;choices.add(categoryPicker);
        } else choices.setBorder(null);revalidate();repaint();
    }
    private void catalogue(Runnable next) {
        if(!categories.isEmpty()){next.run();return;}if(catalogueLoading)return;catalogueLoading=true;JsonObject query=new JsonObject();query.addProperty("mode","catalogue");
        loader.load(query,false,data->{catalogueLoading=false;if(!active)return;for(JsonElement value:RankingPanel.array(data,"pages")){JsonObject page=value.getAsJsonObject();categories.put(page.get("key").getAsString(),page);}if(categories.isEmpty()){message("No collection pages have been synced yet. Members: open your in-game collection log and browse its pages or use its search.");return;}configure();next.run();},failure->{catalogueLoading=false;if(active)message(failure);});
    }
    private JsonObject selection(){JsonObject query=new JsonObject();query.addProperty("mode",mode);if(mode.equals("leaderboard"))query.addProperty("metric",metric);if(category!=null)query.addProperty("category",category);if(mode.equals("clog")&&player!=null)query.addProperty("player",player);return query;}
    void reload(boolean refresh) {
        if(!active)return;final long epoch=++revision;
        if(!mode.equals("leaderboard")&&categories.isEmpty()){message("Loading collection categories…");catalogue(()->reload(refresh));return;}
        if(mode.equals("clog")&&(player==null||player.isEmpty())){message("Enter a clan member's name above and press Enter.");return;}
        JsonObject query=selection();String key=query.toString();if(!key.equals(displayedSelection))current=null;if(!refresh&&cache.containsKey(key)){displayedSelection=key;show(cache.get(key));return;}updated.setText("Loading…");if(current==null)message("Loading clan data…");
        loader.load(query,refresh,data->{if(!active||epoch!=revision)return;cache.put(key,data);if(cache.size()>64)cache.remove(cache.keySet().iterator().next());displayedSelection=key;show(data);},failure->{if(!active||epoch!=revision)return;updated.setText("Refresh failed");notice.setText(failure);if(current==null)message(failure);});
    }
    private void show(JsonObject data) {
        current=data;standings=null;body.removeAll();
        if(mode.equals("clog"))renderLog(data);else {
            if(!mode.equals("leaderboard")&&!mode.equals("dryboard"))addSidebar();standings=new BrowserStandings(data,valueLabel(),this::openCollection);body.add(standings,BorderLayout.CENTER);
            caption.setText(mode.equals("dryboard")?"Kills since last recorded unique":mode.equals("leaderboard")?"Full clan standings":categoryLabel());String missing=data.has("missing")?data.get("missing").getAsString():"0";
            notice.setText(mode.equals("leaderboard")?"Missing account types stay unclassified. "+missing+" accounts have no synced score for this statistic.":"~ = completion first observed after the event. Unrecorded counters stay unknown. "+missing+" accounts lack comparable data.");
        }
        String timestamp=RankingPanel.string(data,"generatedAt","");try{timestamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.parse(timestamp));}catch(RuntimeException ignored){}updated.setText("Updated: "+timestamp);configure();filter();body.revalidate();body.repaint();
    }
    private void addSidebar(){JScrollPane scroll=new PageScrollPane(sidebar);scroll.setBorder(null);scroll.setPreferredSize(new Dimension(195,0));scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(24);body.add(scroll,BorderLayout.WEST);}
    private void renderLog(JsonObject data) {
        addSidebar();completedPages.clear();for(JsonElement value:RankingPanel.array(data,"completedPages"))completedPages.add(value.getAsString());caption.setText(RankingPanel.string(data,"player",player==null?"Collection log":player)+" · "+categoryLabel());
        JsonArray pages=RankingPanel.array(data,"pages");if(pages.size()==0){body.add(label("No synced collection page for this account yet.",20,INK),BorderLayout.CENTER);notice.setText("Open the in-game collection log to record historical items. Unseen items stay unknown.");return;}
        JsonObject page=pages.get(0).getAsJsonObject();JPanel items=panel(new GridLayout(0,8,7,7));int obtained=0,known=0;final long epoch=revision;
        for(JsonElement value:RankingPanel.array(page,"items")) {
            JsonObject item=value.getAsJsonObject();Long quantity=item.has("quantity")&&!item.get("quantity").isJsonNull()?item.get("quantity").getAsLong():null;if(quantity!=null)known++;if(quantity!=null&&quantity>0)obtained++;
            JPanel tile=new JPanel(new BorderLayout());tile.setBackground(quantity!=null&&quantity>0?new Color(29,49,40):CARD);tile.setBorder(BorderFactory.createLineBorder(quantity!=null&&quantity>0?GREEN:LINE));tile.setPreferredSize(new Dimension(64,68));JLabel icon=label(quantity==null?"?":"",20,INK);icon.setHorizontalAlignment(SwingConstants.CENTER);tile.add(icon,BorderLayout.CENTER);JLabel amount=label(quantity==null?"Unknown":quantity>0?"x"+quantity:"Missing",14,quantity!=null&&quantity>0?GREEN:INK);amount.setHorizontalAlignment(SwingConstants.CENTER);tile.add(amount,BorderLayout.SOUTH);
            int id=item.get("id").getAsInt();String fallback=RankingPanel.string(item,"name","Item "+id);tile.setToolTipText(fallback+" · "+amount.getText());JsonObject entry=new JsonObject();entry.addProperty("itemId",id);entry.addProperty("title",fallback);art.load(entry,(name,image)->{if(active&&epoch==revision){icon.setText("");icon.setIcon(image);tile.setToolTipText(name+" · "+amount.getText());}});items.add(tile);
        }
        JPanel itemArea=panel(new BorderLayout());itemArea.add(items,BorderLayout.NORTH);JScrollPane scroll=new PageScrollPane(itemArea);scroll.setBorder(BorderFactory.createLineBorder(LINE));scroll.getViewport().setBackground(BG);scroll.getVerticalScrollBar().setUnitIncrement(28);body.add(scroll,BorderLayout.CENTER);notice.setText(obtained+" / "+RankingPanel.array(page,"items").size()+" obtained · "+known+" items observed · "+(page.has("counter")&&!page.get("counter").isJsonNull()?"Current counter: "+page.get("counter").getAsString():"No comparable counter"));
    }
    private void openCollection(String name){player=name;mode="clog";search.setText(name);if(category==null||!categories.containsKey(category))category="vorkath";if(categories.containsKey(category))section=RankingPanel.string(categories.get(category),"section","Bosses");configure();reload(false);}
    private void suggest() {
        if(adjusting||!active||!(mode.equals("dryboard")||mode.equals("leaderboard")&&metric.equals("bosses")))return;
        JTextField editor=(JTextField)categoryPicker.getEditor().getEditorComponent();String text=editor.getText().trim().toLowerCase(Locale.ROOT);
        SwingUtilities.invokeLater(()->{
            if(!active||adjusting||!editor.isShowing()||!editor.getText().trim().toLowerCase(Locale.ROOT).equals(text))return;
            suggestions.setVisible(false);suggestions.removeAll();if(text.isEmpty())return;
            for(int i=0;i<categoryPicker.getItemCount();i++){Choice choice=categoryPicker.getItemAt(i);if(choice.label.equalsIgnoreCase(text)||choice.key.equalsIgnoreCase(text))return;}
            for(int i=0;i<categoryPicker.getItemCount()&&suggestions.getComponentCount()<12;i++){Choice choice=categoryPicker.getItemAt(i);if(!choice.label.toLowerCase(Locale.ROOT).contains(text)&&!choice.key.contains(text))continue;JMenuItem item=new JMenuItem(choice.label);item.setFont(font(18));item.addActionListener(event->{suggestions.setVisible(false);categoryPicker.setSelectedItem(choice);});suggestions.add(item);}
            if(suggestions.getComponentCount()>0){suggestions.setFocusable(false);suggestions.show(editor,0,editor.getHeight());editor.requestFocusInWindow();}
        });
    }
    private void filter(){if(standings!=null&&!mode.equals("clog"))standings.filter(search.getText().trim());}
    private void findMe(){if(viewer==null){notice.setText("Sign in to find your linked clan character.");return;}if(mode.equals("clog")){player=viewer;search.setText(viewer);reload(false);return;}search.setText("");if(standings==null||!standings.find(viewer))notice.setText("Your linked character has no synced score for this view.");}
    private String metricLabel(){for(String[] value:METRICS)if(value[0].equals(metric))return value[1];return metric;}
    private String valueLabel(){return mode.equals("dryboard")?"Dry KC":!mode.equals("leaderboard")?"Counter":metric.equals("xp")?"Total XP":metric.equals("points")||metric.equals("monthly-points")?"Points":metric.equals("bosses")?"Kills":metric.equals("combat")?"CA points":metricLabel();}
    private String categoryLabel(){return category!=null&&categories.containsKey(category)?RankingPanel.string(categories.get(category),"label",category):category==null?"Choose a category":pretty(category);}
    private void message(String text){standings=null;body.removeAll();JTextArea message=new JTextArea(text);message.setEditable(false);message.setOpaque(false);message.setLineWrap(true);message.setWrapStyleWord(true);message.setForeground(INK);message.setFont(font(20));message.setBorder(new EmptyBorder(15,10,10,10));body.add(message,BorderLayout.CENTER);body.revalidate();body.repaint();}
    private void help(){JOptionPane.showMessageDialog(this,"Collection log: enter a clan member name and press Enter. Choose a tab and category.\nLuck: lowest saved completion counters first. Deserved: highest first.\nDryboard: kills since the last recorded matching collection drop, including duplicates.\nLeaderboards: ten statistics with separate Mains, Ironman/GIM/UIM and Hardcore columns.\nSearch retains each account's original rank. Find me locates your linked character.\nRefresh reloads the selected view; it never syncs or controls the game.\n\nMembers: opt into ranking sync, then open the in-game collection log and browse pages\nor use its search to record historical items. Unseen items and counters stay unknown.\n~ marks a completion counter first observed after completion.\nMonthly clog counts gains observed since the first sync of the current UTC month.\nAll browser views are public; uploading observations requires member sign-in.","TRAP HOUSE clan browser",JOptionPane.INFORMATION_MESSAGE);}
}
