package com.traphouse;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.CardLayout;
import javax.swing.*;
import javax.swing.JTabbedPane;
import net.runelite.client.ui.PluginPanel;

final class TrapHouseDashboard extends PluginPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private int selected;
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private JButton[] navigationButtons;

    TrapHouseDashboard(TrapHousePanel bossPanel, TrapHousePanel skillPanel, int selectedTab) {
        this(bossPanel, skillPanel, selectedTab, null);
    }

    TrapHouseDashboard(TrapHousePanel bossPanel, TrapHousePanel skillPanel, int selectedTab, RankingPanel ranking) {
        setLayout(new BorderLayout());
        if (ranking != null) {
            JPanel header = ClanStyle.column();
            ClanStyle.add(header, ClanStyle.brand());
            JPanel navigation = new JPanel(new GridLayout(0,2,5,5));
            navigation.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            navigation.setOpaque(false);
            String[] names = {"Profile","Ranks","Leaderboard","Achievements","Events","Diary","Competitions"};
            JComponent[] rankPages = ranking.pages();
            JButton[] buttons = new JButton[names.length];
            navigationButtons=buttons;
            for (int i=0;i<names.length;i++) {
                final int index=i;
                buttons[i]=ClanStyle.button(names[i],()-> {
                    selected=index; cards.show(pages,Integer.toString(index));
                    for(int b=0;b<buttons.length;b++) buttons[b].setBackground(b==index?new java.awt.Color(137,38,53):new java.awt.Color(33,37,46));
                });
                navigation.add(buttons[i]);
                buttons[i].setMargin(new java.awt.Insets(6,1,6,1));buttons[i].setFont(new Font("SansSerif",Font.BOLD,10));
                if(i<rankPages.length&&i!=4)pages.add(rankPages[i],Integer.toString(i));
            }
            JTabbedPane eventTabs = new JTabbedPane();
            eventTabs.setFont(new Font("SansSerif",Font.BOLD,10));
            eventTabs.addTab("Boss event",bossPanel);eventTabs.addTab("Skill event",skillPanel);
            eventTabs.addTab("Clan events",rankPages[4]);
            pages.add(eventTabs,"4");
            pages.add(ranking.racePage(),"6");
            header.add(navigation); add(header,BorderLayout.NORTH);add(pages,BorderLayout.CENTER);
            ranking.navigation(()->buttons[0].doClick(),()->buttons[2].doClick());
            buttons[Math.max(0,Math.min(selectedTab,6))].doClick();
            return;
        }
        tabs.setFont(new Font("SansSerif", Font.BOLD, 11));
        if (ranking != null) {
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            ranking.addTabs(tabs);
        }
        tabs.addTab("Boss event", bossPanel);
        tabs.addTab("Skill event", skillPanel);
        tabs.setToolTipTextAt(ranking == null ? 0 : 3, "Boss of the Week: competition kill gains");
        tabs.setToolTipTextAt(ranking == null ? 1 : 4, "Skill of the Month: competition XP gains");
        tabs.setSelectedIndex(selectedTab);
        add(tabs, BorderLayout.CENTER);
    }

    int selectedTab() { return getComponent(0) == tabs ? tabs.getSelectedIndex() : selected; }

    void showProfile() { if(navigationButtons!=null)navigationButtons[0].doClick(); }
}
