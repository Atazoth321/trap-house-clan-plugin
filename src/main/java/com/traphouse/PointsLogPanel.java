package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.function.*;
import javax.swing.*;

/** Read-only ledger popup. Revision checks discard replies to superseded filters. */
final class PointsLogPanel extends JPanel {
    interface ItemArt {void load(JsonObject entry,BiConsumer<String,Icon> done);}
    private final ItemArt itemArt;
    static final String[] CATEGORIES={"All","Drops","Pets","Milestones","Diaries","Challenges","Events","Combat","Collection","Efficiency","Misc"};
    private final JComboBox<String> source=new JComboBox<>(CATEGORIES),sort=new JComboBox<>(new String[]{"Newest first","Oldest first","Highest points"});
    private final JTextField search=new JTextField(16);
    private final JCheckBox group=new JCheckBox("Group");
    private final JLabel summary=ProfileStyle.label("Loading...",12,ProfileStyle.ACCENT),pagination=ProfileStyle.label("Page 1",11,ProfileStyle.MUTED);
    private final JPanel cards=new JPanel(new GridLayout(0,4,8,8));
    private final JButton prev=ClanStyle.button("Prev",()->{}),next=ClanStyle.button("Next",()->{});
    private final String member;
    private final BiConsumer<JsonObject,Consumer<JsonObject>> loader;
    private int page=1;private long revision;private boolean active=true;
    PointsLogPanel(String member,String category,BiConsumer<JsonObject,Consumer<JsonObject>> loader) {
        this(member,category,loader,(entry,done)->{});
    }
    PointsLogPanel(String member,String category,BiConsumer<JsonObject,Consumer<JsonObject>> loader,ItemArt itemArt) {
        super(new BorderLayout(8,8));this.member=member;this.loader=loader;
        this.itemArt=itemArt;
        setBackground(ProfileStyle.BACKGROUND);setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setPreferredSize(new Dimension(900,680));
        JPanel top=new JPanel(new BorderLayout(4,8)),filters=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)),paging=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        top.setOpaque(false);filters.setOpaque(false);paging.setOpaque(false);group.setOpaque(false);group.setForeground(ProfileStyle.TEXT);
        source.setSelectedItem(category);filters.add(source);filters.add(sort);filters.add(ProfileStyle.label("Search:",11,ProfileStyle.TEXT));filters.add(search);filters.add(group);
        paging.add(summary);paging.add(prev);paging.add(pagination);paging.add(next);top.add(filters,BorderLayout.NORTH);top.add(paging,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        cards.setBackground(ProfileStyle.BACKGROUND);JPanel content=new JPanel(new BorderLayout());content.setBackground(ProfileStyle.BACKGROUND);content.add(cards,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(content);scroll.setBorder(null);scroll.getViewport().setBackground(ProfileStyle.BACKGROUND);scroll.getVerticalScrollBar().setUnitIncrement(16);add(scroll,BorderLayout.CENTER);
        Runnable filter=()->{page=1;load();};source.addActionListener(e->filter.run());sort.addActionListener(e->filter.run());group.addActionListener(e->filter.run());
        Timer debounce=new Timer(300,e->filter.run());debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){debounce.restart();}public void removeUpdate(javax.swing.event.DocumentEvent e){debounce.restart();}public void changedUpdate(javax.swing.event.DocumentEvent e){debounce.restart();}});
        prev.addActionListener(e->{page--;load();});next.addActionListener(e->{page++;load();});
        load();
    }
    void close(){active=false;revision++;}
    private void load() {
        if(!active)return;long request=++revision;prev.setEnabled(false);next.setEnabled(false);summary.setText("Loading...");cards.removeAll();cards.revalidate();cards.repaint();
        JsonObject query=new JsonObject();query.addProperty("player",member);query.addProperty("category",(String)source.getSelectedItem());query.addProperty("sort",new String[]{"newest","oldest","highest"}[sort.getSelectedIndex()]);query.addProperty("search",search.getText());query.addProperty("group",group.isSelected());query.addProperty("page",page);
        loader.accept(query,data->{if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->accept(request,data));}else accept(request,data);});
    }
    private void accept(long request,JsonObject data) {
        if(!active||request!=revision)return;
        if(data==null||data.has("error")){summary.setText("Points log unavailable. Change a filter to retry.");return;}
        if(!RankingTracker.normalize(member).equals(RankingTracker.normalize(data.get("player").getAsString()))){summary.setText("Member mismatch; reload profile.");return;}
        page=data.get("page").getAsInt();int pages=data.get("pages").getAsInt();long count=data.get("count").getAsLong();
        summary.setText(String.format("%,d entries | %+,d pts",count,data.get("total").getAsLong()));pagination.setText("Page "+page+"/"+pages);prev.setEnabled(page>1);next.setEnabled(page<pages);
        for(JsonElement value:RankingPanel.array(data,"entries")) {
            JsonObject e=value.getAsJsonObject();String category=e.get("category").getAsString(),title=e.get("title").getAsString();long points=e.get("points").getAsLong();
            Color color=ProfileStyle.ACCENT;
            JPanel card=ProfileStyle.card(null);card.setPreferredSize(new Dimension(200,155));
            JLabel name=ProfileStyle.label(title,11,ProfileStyle.TEXT);name.setToolTipText(title);card.add(name);card.add(Box.createVerticalStrut(12));
            JLabel badge=ProfileStyle.label(category.substring(0,1),32,color);badge.setAlignmentX(Component.CENTER_ALIGNMENT);card.add(badge);card.add(Box.createVerticalStrut(8));
            itemArt.load(e,(itemName,icon)->{
                Runnable apply=()->{if(!active||request!=revision)return;name.setText(itemName);name.setToolTipText(itemName+" / "+title);badge.setText("");badge.setIcon(icon);card.revalidate();card.repaint();};
                if(SwingUtilities.isEventDispatchThread())apply.run();else SwingUtilities.invokeLater(apply);
            });
            long occurrences=e.has("occurrences")?e.get("occurrences").getAsLong():1;card.add(ProfileStyle.label(category+(occurrences>1?" x"+occurrences:""),10,ProfileStyle.MUTED));
            card.add(ProfileStyle.label(String.format("%+,d pts",points),13,points<0?ProfileStyle.ACCENT:ProfileStyle.TEXT));card.add(ProfileStyle.label(e.get("at").getAsString().replace("T"," ").replace("Z"," UTC"),9,ProfileStyle.MUTED));cards.add(card);
        }
        if(count==0)cards.add(ProfileStyle.label("No matching awards.",12,ProfileStyle.MUTED));cards.revalidate();cards.repaint();
    }
}
