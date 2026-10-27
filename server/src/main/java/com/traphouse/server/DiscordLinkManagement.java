package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.UUID;

/** Owner-only changes to an established link; never moves a link to another member. */
final class DiscordLinkManagement {
    private static final class Link {
        final UUID member;
        final Timestamp linkedAt;
        final String discord,name,version;
        Link(UUID member,String discord,String name,Timestamp linkedAt) {
            this.member=member;this.discord=discord;this.name=name;this.linkedAt=linkedAt;
            version=Policy.hash(member+":"+discord+":"+name+":"+linkedAt.toInstant()).substring(0,16);
        }
        String custom(String action) {return "linked:"+action+":"+discord+":"+member+":"+version;}
    }

    private static Link linked(Connection c,String discord)throws SQLException {
        try(PreparedStatement s=RankingStore.query(c,"SELECT m.id,m.name,l.linked_at FROM discord_member_links l JOIN members m ON m.id=l.member_id WHERE l.discord_user_id=? FOR UPDATE OF m,l",discord);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"This Discord account has no accepted RuneLite link.");
            return new Link(r.getObject(1,UUID.class),discord,r.getString(2),r.getTimestamp(3));
        }
    }

    private static void protectOwner(Connection c,Link link)throws SQLException {
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM member_permissions WHERE member_id=? AND permission='OWNER'",link.member);ResultSet r=s.executeQuery()) {
            if(r.next())throw new Policy.Rejected(403,"Owner identity changes require the server operator; the Owner link cannot be changed here.");
        }
    }

    static JsonObject form(Connection c,String owner,String target,boolean edit)throws SQLException {
        DiscordJoining.owner(c,owner);
        Link link=linked(c,DiscordLogin.snowflake(target));protectOwner(c,link);
        return form(link,edit);
    }

    private static JsonObject form(Link link,boolean edit) {
        JsonObject response=DiscordJoining.modal(link.custom(edit?"edit":"revoke"),edit?"Correct "+link.name:"Revoke "+link.name,"reason",edit?"How did you verify the correct character?":"Reason for revoking this accepted link",1000);
        if(edit) {
            JsonObject name=DiscordJoining.modal("unused","unused","rsn","Correct OSRS name (same person)",12);
            JsonArray rows=new JsonArray();rows.add(name.getAsJsonObject("data").getAsJsonArray("components").get(0));
            rows.add(response.getAsJsonObject("data").getAsJsonArray("components").get(0));
            response.getAsJsonObject("data").add("components",rows);
        }
        return response;
    }

    static JsonObject rejectAccepted(Connection c,String owner,UUID request)throws SQLException {
        String target;UUID member;Timestamp reviewed;
        try(PreparedStatement s=RankingStore.query(c,"SELECT discord_user_id,member_id,status,reviewed_at FROM discord_join_requests WHERE id=?",request);ResultSet r=s.executeQuery()) {
            if(!r.next()||!"APPROVED".equals(r.getString(3)))return null;
            target=r.getString(1);member=r.getObject(2,UUID.class);reviewed=r.getTimestamp(4);
        }
        DiscordJoining.owner(c,owner);
        Link link=linked(c,target);
        if(!link.member.equals(member)||!link.linkedAt.equals(reviewed))throw new Policy.Rejected(409,"This approval no longer matches the current link. Use /revoke-link instead.");
        protectOwner(c,link);return form(link,false);
    }

    static JsonObject submit(Connection c,String owner,JsonObject data)throws SQLException {
        RankingStore.Actor actor=DiscordJoining.owner(c,owner);
        String[] parts=Policy.text(data,"custom_id",100).split(":");
        if(parts.length!=5||!parts[0].equals("linked")||!(parts[1].equals("edit")||parts[1].equals("revoke")))throw new Policy.Rejected(400,"Invalid link action");
        Link link=linked(c,DiscordLogin.snowflake(parts[2]));
        if(!link.member.equals(Policy.uuid(parts[3]))||!link.version.equals(parts[4]))throw new Policy.Rejected(409,"This link changed after the form opened. Run the command again.");
        protectOwner(c,link);
        String reason=DiscordJoining.field(data,"reason",1000).trim();
        if(reason.length()<10)throw new Policy.Rejected(400,"Give a reason of 10–1000 characters.");
        boolean edit=parts[1].equals("edit");String newName=null;
        if(edit) {
            newName=DiscordJoining.characterName(DiscordJoining.field(data,"rsn",12));
            try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM members WHERE normalized_name=? AND id<>?",Policy.normalize(newName),link.member);ResultSet r=s.executeQuery()) {
                if(r.next())throw new Policy.Rejected(409,"That name already has a member record. No records were merged; contact the server operator.");
            }
            RankingStore.execute(c,"UPDATE members SET name=?,normalized_name=? WHERE id=?",newName,Policy.normalize(newName),link.member);
        } else RankingStore.execute(c,"DELETE FROM discord_member_links WHERE discord_user_id=? AND member_id=?",link.discord,link.member);
        RankingStore.execute(c,"UPDATE credentials SET revoked=true WHERE member_id=? AND NOT revoked",link.member);
        RankingStore.execute(c,"DELETE FROM discord_login_sessions WHERE member_id=?",link.member);
        JsonObject details=new JsonObject();details.addProperty("discordUserId",link.discord);details.addProperty("oldName",link.name);
        if(edit)details.addProperty("newName",newName);details.addProperty("reason",reason);
        RankSecurity.audit(c,actor,edit?"DISCORD_LINK_NAME_CORRECTED":"DISCORD_LINK_REVOKED",link.member,details);
        return DiscordJoining.message(edit?"Name corrected to "+newName+". Ask the member to sign in with Discord again. Points and history were preserved.":"Accepted link revoked for "+link.name+". Existing credentials and sign-in sessions were revoked. Points and history were preserved. A new /link request requires Owner approval.");
    }

    static String target(JsonObject data) {
        String target=null;
        if(data.has("options"))for(JsonElement element:data.getAsJsonArray("options")) {
            JsonObject option=element.getAsJsonObject();
            if("member".equals(Policy.text(option,"name",32))) {
                if(target!=null||option.get("type").getAsInt()!=6)throw new Policy.Rejected(400,"Choose one Discord member.");
                target=DiscordLogin.snowflake(Policy.text(option,"value",20));
            }
        }
        if(target==null)throw new Policy.Rejected(400,"Choose a Discord member.");return target;
    }
}
