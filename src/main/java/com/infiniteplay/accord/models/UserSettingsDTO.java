package com.infiniteplay.accord.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class UserSettingsDTO {
    @JsonProperty("spamFilterMode")
    private String spamFilterMode;
    @JsonProperty("nsfwDmFriends")
    private String nsfwDmFriends;
    @JsonProperty("nsfwDmOthers")
    private String nsfwDmOthers;
    @JsonProperty("nsfwGroups")
    private String nsfwGroups;
    @JsonProperty("allowNonFriendsDM")
    private boolean allowNonFriendsDM;

    @JsonProperty("allowFriendRequestEveryone")
    private boolean allowFriendRequestEveryone = true;

    @JsonProperty("allowFriendRequestFof")
    private boolean allowFriendRequestFof = true;

    @JsonProperty("allowFriendRequestGroup")
    private boolean allowFriendRequestGroup = true;

    @JsonProperty("entranceSound")
    private String entranceSound = "default";

    @JsonProperty("canPreviewStream")
    private boolean canPreviewStream = true;

    @JsonProperty("notifyReaction")
    private String notifyReaction = "dm";


    @JsonProperty("doNotification")
    private Boolean doNotification = true;

    @JsonProperty("messageRequests")
    private Boolean messageRequests = false;

    @JsonProperty("mutedChatRoomIds")
    private List<Integer> mutedChatRoomIds = new ArrayList<>();

    @JsonProperty("displaySpoiler")
    private String displaySpoiler = "click";
}
