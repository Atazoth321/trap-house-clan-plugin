package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Signed Discord identities may request links. Only a mapped active backend Owner reviews them. */
final class DiscordJoining {
    static String characterName(String value) {
        if(value==null||!value.matches("[A-Za-z0-9 _-]{1,12}")||value.trim().isEmpty())throw new Policy.Rejected(400,"Enter a valid OSRS character name (1–12 characters).");
        return value.trim();
    }
    static JsonObject object(String json){return JsonParser.parseString(json).getAsJsonObject();}
    static JsonObject message(String content) {
        JsonObject data=new JsonObject();data.addProperty("content",content);data.addProperty("flags",64);
        data.add("allowed_mentions",object("{\"parse\":[]}"));JsonObject reply=new JsonObject();reply.addProperty("type",4);reply.add("data",data);return reply;
    }
    static JsonObject button(String id,String label,int style) {
        JsonObject button=new JsonObject();button.addProperty("type",2);button.addProperty("style",style);button.addProperty("custom_id",id);button.addProperty("label",label);return button;
    }
    static JsonObject row(JsonObject... buttons) {
        JsonObject row=new JsonObject();row.addProperty("type",1);JsonArray components=new JsonArray();for(JsonObject b:buttons)components.add(b);row.add("components",components);return row;
    }
    static JsonObject modal(String id,String title,String field,String label,int max) {
        JsonObject input=new JsonObject();input.addProperty("type",4);input.addProperty("custom_id",field);input.addProperty("label",label);
        input.addProperty("style",field.equals("reason")?2:1);input.addProperty("required",true);input.addProperty("max_length",max);
        JsonArray rows=new JsonArray();rows.add(row(input));JsonObject data=new JsonObject();data.addProperty("custom_id",id);data.addProperty("title",title);data.add("components",rows);
        JsonObject response=new JsonObject();response.addProperty("type",9);response.add("data",data);return response;
    }
    static String field(JsonObject data,String key,int max) {
        String value=null;
        for(JsonElement row:data.getAsJsonArray("components"))for(JsonElement element:row.getAsJsonObject().getAsJsonArray("components")) {
            JsonObject input=element.getAsJsonObject();
            if(key.equals(Policy.text(input,"custom_id",100))) {if(value!=null)throw new Policy.Rejected(400,"Duplicate form field");value=Policy.text(input,"value",max);}
        }
        if(value==null)throw new Policy.Rejected(400,"Missing form field");return value;
    }
    static RankingStore.Actor owner(Connection c,String discord)throws SQLException {
        try(PreparedStatement s=RankingStore.query(c,"SELECT m.id,m.name FROM discord_member_links l JOIN members m ON m.id=l.member_id JOIN member_permissions p ON p.member_id=m.id WHERE l.discord_user_id=? AND m.active AND p.permission='OWNER' FOR UPDATE OF m,p",discord);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(403,"Only the linked clan Owner can review membership requests.");
            RankingStore.Actor owner=new RankingStore.Actor(r.getObject(1,UUID.class),r.getString(2),RankSecurity.Role.OWNER);
            // Serialize decisions and permission changes against the member row.
            RankingStore.lockMember(c,owner.id);return owner;
        }
    }
    static JsonObject decisions(UUID id) {return row(button("join:approve:"+id,"Approve after verification",3),button("join:reject:"+id,"Reject",4));}
    static JsonObject linkForm(Connection c,String discord)throws SQLException {
        try(PreparedStatement s=RankingStore.query(c,"SELECT m.name,m.active FROM discord_member_links l JOIN members m ON m.id=l.member_id WHERE l.discord_user_id=?",discord);ResultSet r=s.executeQuery()) {
            if(r.next())return message(r.getBoolean(2)?"Your account is linked to "+r.getString(1)+". Click Sign in with Discord in RuneLite to connect.":"Your linked clan membership is inactive. Contact the Owner.");
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT character_name FROM discord_join_requests WHERE discord_user_id=? AND status='PENDING'",discord);ResultSet r=s.executeQuery()) {
            if(r.next())return message("Your request for "+r.getString(1)+" is waiting for the Owner to verify and approve it. No access has been granted yet.");
        }
        return modal("join:request","Link RuneLite","rsn","Your OSRS character name",12);
    }
    static UUID request(Connection c,String discord,String name)throws SQLException {
        name=characterName(name);String normalized=Policy.normalize(name);
        // Serialize same-user submissions before checking link/request limits.
        try(PreparedStatement s=RankingStore.query(c,"SELECT pg_advisory_xact_lock(hashtextextended(?,91821))",discord)){s.executeQuery().close();}
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM discord_member_links WHERE discord_user_id=?",discord);ResultSet r=s.executeQuery()) {
            if(r.next())throw new Policy.Rejected(409,"Your Discord account is already linked. Contact the Owner for account changes.");
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT id,normalized_name FROM discord_join_requests WHERE discord_user_id=? AND status='PENDING'",discord);ResultSet r=s.executeQuery()) {
            if(r.next()) {if(!r.getString(2).equals(normalized))throw new Policy.Rejected(409,"You already have a pending request. Ask the Owner to review it first.");return r.getObject(1,UUID.class);}
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*) FROM discord_join_requests WHERE discord_user_id=? AND created_at>now()-interval '1 day'",discord);ResultSet r=s.executeQuery()) {
            r.next();if(r.getInt(1)>=5)throw new Policy.Rejected(429,"Request limit reached. Try again tomorrow.");
        }
        UUID id=UUID.randomUUID();
        try(PreparedStatement s=RankingStore.query(c,"INSERT INTO discord_join_requests(id,discord_user_id,character_name,normalized_name) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",id,discord,name,normalized)) {
            if(s.executeUpdate()!=1)throw new Policy.Rejected(409,"That character has a pending request. Contact the Owner.");
        }
        JsonObject notification=new JsonObject();notification.addProperty("content","RuneLite link request: **"+name+"** — Discord ID `"+discord+"`. Owner: independently verify character ownership and current clan membership before approving.");
        notification.add("allowed_mentions",object("{\"parse\":[]}"));JsonArray buttons=new JsonArray();buttons.add(decisions(id));notification.add("components",buttons);
        RankingStore.execute(c,"INSERT INTO discord_join_outbox(id,payload) VALUES (?,?::jsonb)",id,notification.toString());return id;
    }
    static void review(Connection c,String discord,UUID request,boolean approve,String reason)throws SQLException {
        RankingStore.Actor actor=owner(c,discord);
        if(reason==null||reason.trim().length()<10||reason.length()>1000)throw new Policy.Rejected(400,"Give a verification/rejection reason of 10–1000 characters.");
        try(PreparedStatement s=RankingStore.query(c,"SELECT discord_user_id,character_name,normalized_name,status FROM discord_join_requests WHERE id=? FOR UPDATE",request);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"Request unavailable");
            if(!r.getString(4).equals("PENDING"))throw new Policy.Rejected(409,"This request was already reviewed.");
            String applicant=r.getString(1),name=r.getString(2),normalized=r.getString(3);UUID member=null;
            if(approve) {
                // Unique constraints also protect against a concurrent operator link.
                try(PreparedStatement existing=RankingStore.query(c,"SELECT 1 FROM discord_member_links l JOIN members m ON m.id=l.member_id WHERE l.discord_user_id=? OR m.normalized_name=?",applicant,normalized);ResultSet linked=existing.executeQuery()) {
                    if(linked.next())throw new Policy.Rejected(409,"Discord or character is already linked. No existing link was replaced.");
                }
                try(PreparedStatement existing=RankingStore.query(c,"SELECT id,active FROM members WHERE normalized_name=? FOR UPDATE",normalized);ResultSet found=existing.executeQuery()) {
                    if(found.next()) {if(!found.getBoolean(2))throw new Policy.Rejected(409,"Inactive characters require operator review before relinking.");member=found.getObject(1,UUID.class);}
                }
                if(member==null){member=UUID.randomUUID();RankingStore.execute(c,"INSERT INTO members(id,name,normalized_name) VALUES (?,?,?)",member,name,normalized);}
                RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",applicant,member);
            }
            RankingStore.execute(c,"UPDATE discord_join_requests SET status=?,reviewed_at=now(),reviewer=?,reason=?,member_id=? WHERE id=?",approve?"APPROVED":"REJECTED",actor.id,reason.trim(),member,request);
            JsonObject details=new JsonObject();details.addProperty("request",request.toString());details.addProperty("discordUserId",applicant);details.addProperty("character",name);details.addProperty("reason",reason.trim());
            if(member!=null)details.addProperty("member",member.toString());
            RankingStore.execute(c,"INSERT INTO audit(actor,action,target,details) VALUES (?,?,?,?::jsonb)",actor.id,approve?"DISCORD_LINK_APPROVED":"DISCORD_LINK_REJECTED",member==null?request:member,details.toString());
        }
    }
    static JsonObject pending(Connection c,String discord)throws SQLException {
        owner(c,discord);JsonObject reply=message("Pending RuneLite links (up to five oldest). Verify character ownership and clan membership before approval. Use /pending again for the next requests.");JsonArray rows=new JsonArray();StringBuilder content=new StringBuilder(reply.getAsJsonObject("data").get("content").getAsString());
        try(PreparedStatement s=RankingStore.query(c,"SELECT id,character_name,discord_user_id FROM discord_join_requests WHERE status='PENDING' ORDER BY created_at,id LIMIT 5");ResultSet r=s.executeQuery()) {
            while(r.next()) {UUID id=r.getObject(1,UUID.class);content.append("\n**").append(r.getString(2)).append("** — Discord ID `").append(r.getString(3)).append("`");
                JsonObject row=decisions(id);row.getAsJsonArray("components").get(0).getAsJsonObject().addProperty("label","Approve "+r.getString(2));row.getAsJsonArray("components").get(1).getAsJsonObject().addProperty("label","Reject "+r.getString(2));rows.add(row);}
        }
        if(rows.size()==0)content.append("\nNo pending requests.");reply.getAsJsonObject("data").addProperty("content",content.toString());reply.getAsJsonObject("data").add("components",rows);return reply;
    }
    static JsonObject dispatch(Connection c,JsonObject interaction,String discord)throws SQLException {
        int type=interaction.get("type").getAsInt();JsonObject data=interaction.getAsJsonObject("data");
        if(type==2) {
            String command=Policy.text(data,"name",32);if(command.equals("pending"))return pending(c,discord);
            if(command.equals("link"))return linkForm(c,discord);
            if(command.equals("edit-link")||command.equals("revoke-link")) {
                owner(c,discord);
                return DiscordLinkManagement.form(c,discord,DiscordLinkManagement.target(data),command.equals("edit-link"));
            }
        } else {
            String custom=Policy.text(data,"custom_id",100);
            if(type==5&&custom.startsWith("linked:"))return DiscordLinkManagement.submit(c,discord,data);
            if(type==3&&custom.equals("join:start"))return linkForm(c,discord);
            if(type==5&&custom.equals("join:request")) {
                UUID request=request(c,discord,field(data,"rsn",12));return message("Request submitted: "+request+". The Owner must verify and approve your character. After approval, click Sign in with Discord in RuneLite. No access is granted yet.");
            }
            if(type==3&&(custom.startsWith("join:approve:")||custom.startsWith("join:reject:"))) {
                owner(c,discord);boolean approve=custom.startsWith("join:approve:");UUID id=Policy.uuid(custom.substring(custom.lastIndexOf(':')+1));
                if(!approve) {
                    JsonObject revoke=DiscordLinkManagement.rejectAccepted(c,discord,id);
                    if(revoke!=null)return revoke;
                }
                return modal((approve?"join:accept:":"join:deny:")+id,approve?"Confirm verified character":"Reject linking request","reason",approve?"How did you verify ownership and membership?":"Reason for rejection",1000);
            }
            if(type==5&&(custom.startsWith("join:accept:")||custom.startsWith("join:deny:"))) {
                boolean approve=custom.startsWith("join:accept:");review(c,discord,Policy.uuid(custom.substring(custom.lastIndexOf(':')+1)),approve,field(data,"reason",1000));
                return message(approve?"Approved and linked. Ask the member to click Sign in with Discord in RuneLite. No staff permissions were granted.":"Request rejected. No access was granted.");
            }
        }
        throw new Policy.Rejected(400,"Unsupported Discord action");
    }
}
