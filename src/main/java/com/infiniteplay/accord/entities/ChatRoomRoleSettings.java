package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
public class ChatRoomRoleSettings {

    @GeneratedValue(strategy =  GenerationType.AUTO)
    @Id
    private Integer id;

    @Column(unique = true)
    private Integer chatRoomId;

    @JsonProperty("roleAllowFriendsInvite")
    @Column
    private String roleAllowFriendsInvite = "all";

    @JsonProperty("roleAllowPublicInvite")
    @Column
    private String roleAllowPublicInvite = "mod";

    @JsonProperty("roleAllowDeleteMessage")
    @Column
    private String roleAllowDeleteMessage = "mod";

    @JsonProperty("roleAllowKickUser")
    @Column
    private String roleAllowKickUser = "mod";

    @JsonProperty("roleAllowAbortCall")
    @Column
    private String roleAllowAbortCall = "owner";

    @JsonProperty("roleAllowAddContent")
    @Column
    private String roleAllowAddContent =  "all";

    @JsonProperty("roleAllowDeleteContent")
    @Column
    private String roleAllowDeleteContent = "mod";

    @JsonProperty("roleAllowPinMessage")
    @Column
    private String roleAllowPinMessage = "mod";

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(Integer chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getRoleAllowFriendsInvite() {
        return roleAllowFriendsInvite;
    }

    public String getRoleAllowPinMessage() {
        return roleAllowPinMessage;
    }

    public void setRoleAllowPinMessage(String roleAllowPinMessage) {
        this.roleAllowPinMessage = roleAllowPinMessage;
    }

    public void setRoleAllowFriendsInvite(String roleAllowFriendsInvite) {
        this.roleAllowFriendsInvite = roleAllowFriendsInvite;
    }

    public String getRoleAllowPublicInvite() {
        return roleAllowPublicInvite;
    }

    public void setRoleAllowPublicInvite(String roleAllowPublicInvite) {
        this.roleAllowPublicInvite = roleAllowPublicInvite;
    }

    public String getRoleAllowDeleteMessage() {
        return roleAllowDeleteMessage;
    }

    public void setRoleAllowDeleteMessage(String roleAllowDeleteMessage) {
        this.roleAllowDeleteMessage = roleAllowDeleteMessage;
    }

    public String getRoleAllowKickUser() {
        return roleAllowKickUser;
    }

    public void setRoleAllowKickUser(String roleAllowKickUser) {
        this.roleAllowKickUser = roleAllowKickUser;
    }

    public String getRoleAllowAbortCall() {
        return roleAllowAbortCall;
    }

    public void setRoleAllowAbortCall(String roleAllowAbortCall) {
        this.roleAllowAbortCall = roleAllowAbortCall;
    }

    public String getRoleAllowAddContent() {
        return roleAllowAddContent;
    }

    public void setRoleAllowAddContent(String roleAllowAddContent) {
        this.roleAllowAddContent = roleAllowAddContent;
    }

    public String getRoleAllowDeleteContent() {
        return roleAllowDeleteContent;
    }

    public void setRoleAllowDeleteContent(String roleAllowDeleteContent) {
        this.roleAllowDeleteContent = roleAllowDeleteContent;
    }
}
