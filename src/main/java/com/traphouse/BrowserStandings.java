package com.traphouse;
import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import static com.traphouse.BrowserStyle.*;

/** Independently scrolling tables retain their server positions during search and sorting. */
final class BrowserStandings extends JPanel {
    private final java.util.List<JTable> tables=new ArrayList<>();
    BrowserStandings(JsonObject data,String valueLabel,Consumer<String> open) {
        super(new BorderLayout(0,8));setOpaque(false);String kind=RankingPanel.string(data,"kind","leaderboard");
        JPanel columns=panel(new GridLayout(1,Math.max(1,Math.min(3,RankingPanel.array(data,"panels").size())),8,0));add(columns,BorderLayout.CENTER);
        for(JsonElement value:RankingPanel.array(data,"panels")) {
            JsonObject group=value.getAsJsonObject();JsonArray rows=RankingPanel.array(group,"rows");String key=RankingPanel.string(group,"key","");
            JPanel card=panel(new BorderLayout());card.setBorder(BorderFactory.createLineBorder(LINE));Color color=key.equals("hardcore")?new Color(245,124,111):key.equals("iron")?new Color(199,204,202):GOLD;
            JLabel heading=label(RankingPanel.string(group,"label","Standings")+" ("+rows.size()+")",22,color);heading.setOpaque(true);heading.setBackground(CARD);heading.setBorder(new EmptyBorder(8,10,8,8));card.add(heading,BorderLayout.NORTH);
            DefaultTableModel model=new DefaultTableModel(new String[]{"Rank","Account",valueLabel},0){public boolean isCellEditable(int r,int c){return false;}public Class<?> getColumnClass(int c){return c==0?Integer.class:c==2?Long.class:String.class;}};
            for(JsonElement element:rows){JsonObject row=element.getAsJsonObject();model.addRow(new Object[]{row.has("rank")?row.get("rank").getAsInt():null,RankingPanel.string(row,"player",""),row.has("value")&&!row.get("value").isJsonNull()?row.get("value").getAsLong():null});}
            JTable table=new JTable(model);tables.add(table);table.setRowHeight(40);table.setFont(font(24));table.setBackground(CARD);table.setForeground(INK);table.setShowGrid(false);table.setIntercellSpacing(new Dimension(0,0));table.setFillsViewportHeight(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);table.getAccessibleContext().setAccessibleName(heading.getText()+" leaderboard");
            table.getTableHeader().setFont(font(17));table.getTableHeader().setForeground(INK);table.getTableHeader().setBackground(CARD);table.getTableHeader().setPreferredSize(new Dimension(0,30));table.getTableHeader().setReorderingAllowed(false);
            table.getColumnModel().getColumn(0).setMinWidth(60);table.getColumnModel().getColumn(0).setPreferredWidth(60);table.getColumnModel().getColumn(0).setMaxWidth(70);table.getColumnModel().getColumn(1).setPreferredWidth(160);table.getColumnModel().getColumn(2).setPreferredWidth(105);
            DefaultTableCellRenderer renderer=new DefaultTableCellRenderer(){public Component getTableCellRendererComponent(JTable owner,Object value,boolean selected,boolean focus,int row,int column){
                JLabel cell=(JLabel)super.getTableCellRendererComponent(owner,value,selected,focus,row,column);cell.setBorder(new EmptyBorder(4,7,4,7));cell.setBackground(selected?new Color(118,96,55):row%2==0?new Color(57,65,41):CARD);cell.setForeground(column==0?GOLD:column==2?GREEN:INK);cell.setHorizontalAlignment(column==2?SwingConstants.RIGHT:SwingConstants.LEFT);
                cell.setToolTipText(column==1?String.valueOf(value):null);JsonObject original=rows.get(table.convertRowIndexToModel(row)).getAsJsonObject();
                if(column==0)cell.setText(value==null?"—":"#"+value);
                if(column==2){if(value!=null)cell.setText((!kind.equals("leaderboard")&&!kind.equals("dryboard")&&original.has("estimated")&&original.get("estimated").getAsBoolean()?"~":"")+String.format(Locale.US,"%,d",value));else cell.setText(original.has("complete")&&original.get("complete").getAsBoolean()?"Greenlogged":original.has("obtained")?original.get("obtained").getAsString()+" / "+original.get("total").getAsString():"Unknown");}return cell;
            }};for(int column=0;column<3;column++)table.getColumnModel().getColumn(column).setCellRenderer(renderer);
            table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent event){int row=table.rowAtPoint(event.getPoint());if(event.getButton()==MouseEvent.BUTTON1&&event.getClickCount()==2&&row>=0)open.accept(String.valueOf(table.getValueAt(row,1)));}});
            table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"collection");table.getActionMap().put("collection",new AbstractAction(){public void actionPerformed(ActionEvent event){int row=table.getSelectedRow();if(row>=0)open.accept(String.valueOf(table.getValueAt(row,1)));}});
            JScrollPane scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());scroll.setBorder(null);scroll.getViewport().setBackground(CARD);card.add(scroll,BorderLayout.CENTER);
            if(rows.size()==0){JLabel empty=label(kind.equals("dryboard")?"No comparable drop KC yet":"No recorded scores",18,INK);empty.setHorizontalAlignment(SwingConstants.CENTER);card.add(empty,BorderLayout.SOUTH);}
            if(key.equals("unknown")){card.setPreferredSize(new Dimension(0,145));add(card,BorderLayout.SOUTH);}else columns.add(card);
        }
    }
    void filter(String text){for(JTable table:tables){@SuppressWarnings("unchecked") TableRowSorter<DefaultTableModel> sorter=(TableRowSorter<DefaultTableModel>)table.getRowSorter();sorter.setRowFilter(RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(text),1));}}
    boolean find(String viewer){for(JTable table:tables)for(int row=0;row<table.getRowCount();row++)if(RankingTracker.normalize(viewer).equals(RankingTracker.normalize(String.valueOf(table.getValueAt(row,1))))){table.setRowSelectionInterval(row,row);table.scrollRectToVisible(table.getCellRect(row,1,true));return true;}return false;}
}
