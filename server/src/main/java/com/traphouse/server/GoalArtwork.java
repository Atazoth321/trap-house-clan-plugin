package com.traphouse.server;

/** Display metadata only: item art never changes milestone tracking or rewards. */
final class GoalArtwork {
    static int itemId(String source,String key) {
        if(source==null||key==null)return -1;
        if(source.equals("ITEM"))try{return Integer.parseInt(key);}catch(NumberFormatException invalid){return -1;}
        if(source.equals("QUEST"))return 9813;
        if(!source.equals("MILESTONE"))return -1;
        switch(key) {
            case "QUIVER":return 28947;
            case "MASORI_KIT":return 27372;
            case "MENAPHITE_KIT":return 27255;
            case "PURIFYING_SIGIL":return 30793;
            case "ANCIENT_BLOOD_KIT":return 28336;
            case "ALL_QUESTS":return 9813;
            case "ALL_DIARIES":return 19476;
            case "COLLECTION_COUNT":return 22711;
            default:return -1;
        }
    }
}
