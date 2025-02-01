package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infiniteplay.accord.models.UserStatus;
import com.infiniteplay.accord.utils.JacksonLazyFieldsFilter;
import com.infiniteplay.accord.utils.RandomUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.*;

//@Table(name="accord_user", uniqueConstraints ={
//@UniqueConstraint(columnNames = {"username","account_type"}),
//@UniqueConstraint(columnNames = {"email","account_type"})})
@Table(name="accord_user",indexes={
        @Index(columnList = "username"),
        @Index(columnList = "nickname"),
        @Index(columnList = "active_call_instance_id")
})
@Entity
public class User extends BaseEntity {

    public User() {

    }

    public User(Integer id, AccountType accountType, String email, String nickname, String username, String password, Date birthDate) {
        this.id = id;
        this.accountType = accountType;
        this.nickname = nickname;
        this.username = username;
        this.email = email;
        this.password = password;
        this.birthDate = birthDate;
        this.profileColor = RandomUtils.getRandomHEXString();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(nullable = false)
    private Date registeredAt;

    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false,unique = true,length=50)
    private String email;

    @Column(length=30)
    private String nickname;

    @Column(nullable = false)
    private String username;

    @Column(length=60)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private Date birthDate;

    @Column(nullable = false)
    private UserStatus status = UserStatus.OFFLINE;

    @Column(nullable = true, length=50)
    private String statusMessage;

    @Column(nullable = true)
    @JsonIgnore
    private String aboutMe;

    @Column(nullable = true, unique = true)
    @JsonIgnore
    private Integer accountId;

    @Column(nullable = true, length=255)
    private String profileImageUrl;

    @Column(nullable = false)
    private String profileColor = "#84CC16";

    @Column(nullable = false)
    @JsonProperty("isCallMuted")
    private Boolean isCallMuted = false;

    @Column(nullable = false)
    @JsonProperty("isDeafened")
    private Boolean isDeafened = false;

    @Column(nullable = false)
    @JsonProperty("isVideoEnabled")
    private Boolean isVideoEnabled = false;

    @Column(nullable = false)
    @JsonProperty("isScreenShareEnabled")
    private String isScreenShareEnabled = "no";


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name="friends",
            joinColumns = @JoinColumn(name="friend_of_id"),
            inverseJoinColumns = @JoinColumn(name="friends_id")
    )
    @OrderColumn
    @JsonIgnore
    @Fetch(FetchMode.SUBSELECT)
    private Set<User> friends;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name="blocked",
            joinColumns = @JoinColumn(name="blocked_of_id"),
            inverseJoinColumns = @JoinColumn(name="blocked_id")

    )
    @OrderColumn
    @JsonIgnore
    @Fetch(FetchMode.SUBSELECT)
    private Set<User> blocked;

    @ManyToMany(fetch = FetchType.LAZY,mappedBy = "blocked")
    @JsonIgnore
    @Fetch(FetchMode.SUBSELECT)
    private Set<User> blockers;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name="pending",
            joinColumns = @JoinColumn(name="pending_incoming_id"),
            inverseJoinColumns = @JoinColumn(name="pending_outgoing_id")
    )
    @OrderColumn
    @JsonIgnore
    private Set<User> pendingOutgoing;



    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name="chatrooms",
            joinColumns = @JoinColumn(name="chatrooms_participant_id"),
            inverseJoinColumns = @JoinColumn(name="chatrooms_id")

    )
    @JsonIgnore
    @OrderBy("recentMessageDate DESC")
    private Set<ChatRoom> chatRooms;

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinTable(
            name="pending_calls",
            joinColumns = @JoinColumn(name="pending_calls"),
            inverseJoinColumns = @JoinColumn(name="pending_calls_inverse")
    )
    private Set<Call> pendingCallInstances;


    @ManyToMany(fetch = FetchType.LAZY,mappedBy = "pendingOutgoing")
    @JsonIgnore
    private Set<User> pendingIncoming;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Call activeCallInstance;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "user")
    @Fetch(FetchMode.SUBSELECT)
    @JsonIgnore
    private List<Background> backgrounds = new ArrayList<>();


    @Column(nullable = false)
    @JsonIgnore
    private String spamFilterMode = "None";

    @Column(nullable = false)
    @JsonIgnore
    private String nsfwDmFriends = "Show";

    @Column(nullable = false)
    @JsonIgnore
    private String nsfwDmOthers = "Show";

    @Column(nullable = false)
    @JsonIgnore
    private String nsfwGroups = "Show";

    @Column(nullable = false)
    @JsonIgnore
    private boolean allowNonFriendsDM = true;

    @Column(nullable = false)
    @JsonIgnore
    private boolean allowFriendRequestEveryone = true;

    @Column(nullable = false)
    @JsonIgnore
    private boolean allowFriendRequestFof = true;

    @Column(nullable = false)
    @JsonIgnore
    private boolean allowFriendRequestGroup = true;

    @Column(nullable = false)
    @JsonProperty("entranceSound")
    private String entranceSound = "default";

    @Column(nullable = false)
    @JsonProperty("canPreviewStream")
    private boolean canPreviewStream = true;

    @Column(nullable = false)
    @JsonIgnore
    private String notifyReaction = "dm";

    @Column(nullable = false)
    @JsonIgnore
    private Boolean doNotification = true;

    @Column(nullable = false)
    private Boolean messageRequests = false;

    @SuppressWarnings("JpaAttributeTypeInspection")
    @Column(name = "muted_chatroom_ids", columnDefinition = "integer[]", nullable = true)
    @JsonIgnore
    private Integer[] mutedChatRoomIds;

    @Column(nullable = false)
    private String displaySpoiler = "click";


    public Integer[] getMutedChatRoomIds() {
        return mutedChatRoomIds;
    }

    public void setMutedChatRoomIds(Integer[] mutedChatRoomIds) {
        this.mutedChatRoomIds = mutedChatRoomIds;
    }

    public String getDisplaySpoiler() {
        return displaySpoiler;
    }

    public void setDisplaySpoiler(String displaySpoiler) {
        this.displaySpoiler = displaySpoiler;
    }

    @Transient
    private Long firstUnreadMessageTimestamp;

    public String getAboutMe() {
        return aboutMe;
    }

    public Boolean getDoNotification() {
        return doNotification;
    }

    public void setDoNotification(Boolean doNotification) {
        this.doNotification = doNotification;
    }

    public Boolean getMessageRequests() {
        return messageRequests;
    }

    public void setMessageRequests(Boolean messageRequests) {
        this.messageRequests = messageRequests;
    }

    public void setAboutMe(String aboutMe) {
        this.aboutMe = aboutMe;
    }

    public Date getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Date registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Long getFirstUnreadMessageTimestamp() {
        return firstUnreadMessageTimestamp;
    }

    public void setFirstUnreadMessageTimestamp(Long firstUnreadMessageTimestamp) {
        this.firstUnreadMessageTimestamp = firstUnreadMessageTimestamp;
    }

    public String getNotifyReaction() {
        return notifyReaction;
    }

    public void setNotifyReaction(String notifyReaction) {
        this.notifyReaction = notifyReaction;
    }

    public boolean isCanPreviewStream() {
        return canPreviewStream;
    }

    public void setCanPreviewStream(boolean canPreviewStream) {
        this.canPreviewStream = canPreviewStream;
    }

    public String getEntranceSound() {
        return entranceSound;
    }

    public void setEntranceSound(String entranceSound) {
        this.entranceSound = entranceSound;
    }

    public boolean isAllowFriendRequestEveryone() {
        return allowFriendRequestEveryone;
    }

    public void setAllowFriendRequestEveryone(boolean allowFriendRequestEveryone) {
        this.allowFriendRequestEveryone = allowFriendRequestEveryone;
    }

    public boolean isAllowFriendRequestFof() {
        return allowFriendRequestFof;
    }

    public void setAllowFriendRequestFof(boolean allowFriendRequestFof) {
        this.allowFriendRequestFof = allowFriendRequestFof;
    }

    public boolean isAllowFriendRequestGroup() {
        return allowFriendRequestGroup;
    }

    public void setAllowFriendRequestGroup(boolean allowFriendRequestGroup) {
        this.allowFriendRequestGroup = allowFriendRequestGroup;
    }

    public boolean isAllowNonFriendsDM() {
        return allowNonFriendsDM;
    }

    public void setAllowNonFriendsDM(boolean allowNonFriendsDM) {
        this.allowNonFriendsDM = allowNonFriendsDM;
    }

    @Override
    public String toString() {
        return username + "#" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        User other = (User) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getProfileColor() {
        return profileColor;
    }

    public void setProfileColor(String profileColor) {
        this.profileColor = profileColor;
    }

    public Set<User> getFriends() {
        return friends;
    }

    public void setFriends(Set<User> friends) {
        this.friends = friends;
    }

    public Set<User> getBlocked() {
        return blocked;
    }

    public void setBlocked(Set<User> blocked) {
        this.blocked = blocked;
    }

    public Set<User> getPendingOutgoing() {
        return pendingOutgoing;
    }

    public void setPendingOutgoing(Set<User> pendingOutgoing) {
        this.pendingOutgoing = pendingOutgoing;
    }

    public Set<ChatRoom> getChatRooms() {
        return chatRooms;
    }

    public void setChatRooms(Set<ChatRoom> chatRooms) {
        this.chatRooms = chatRooms;
    }

    public Set<Call> getPendingCallInstances() {
        return pendingCallInstances;
    }

    public void setPendingCallInstances(Set<Call> pendingCallInstances) {
        this.pendingCallInstances = pendingCallInstances;
    }

    public Set<User> getPendingIncoming() {
        return pendingIncoming;
    }

    public void setPendingIncoming(Set<User> pendingIncoming) {
        this.pendingIncoming = pendingIncoming;
    }

    public Call getActiveCallInstance() {
        return activeCallInstance;
    }

    public void setActiveCallInstance(Call activeCallInstance) {
        this.activeCallInstance = activeCallInstance;
    }

    public Boolean getCallMuted() {
        return isCallMuted;
    }

    public void setCallMuted(Boolean callMuted) {
        isCallMuted = callMuted;
    }

    public Boolean getVideoEnabled() {
        return isVideoEnabled;
    }

    public void setVideoEnabled(Boolean videoEnabled) {
        isVideoEnabled = videoEnabled;
    }

    public String getScreenShareEnabled() {
        return isScreenShareEnabled;
    }

    public void setScreenShareEnabled(String screenShareEnabled) {
        isScreenShareEnabled = screenShareEnabled;
    }

    public Boolean getDeafened() {
        return isDeafened;
    }

    public void setDeafened(Boolean deafened) {
        isDeafened = deafened;
    }

    public Set<User> getBlockers() {
        return blockers;
    }

    public void setBlockers(Set<User> blockers) {
        this.blockers = blockers;
    }


    public List<Background> getBackgrounds() {
        return backgrounds;
    }

    public void setBackgrounds(List<Background> backgrounds) {
        this.backgrounds = backgrounds;
    }

    public String getSpamFilterMode() {
        return spamFilterMode;
    }

    public void setSpamFilterMode(String spamFilterMode) {
        this.spamFilterMode = spamFilterMode;
    }

    public String getNsfwDmFriends() {
        return nsfwDmFriends;
    }

    public void setNsfwDmFriends(String nsfwDmFriends) {
        this.nsfwDmFriends = nsfwDmFriends;
    }

    public String getNsfwDmOthers() {
        return nsfwDmOthers;
    }

    public void setNsfwDmOthers(String nsfwDmOthers) {
        this.nsfwDmOthers = nsfwDmOthers;
    }

    public String getNsfwGroups() {
        return nsfwGroups;
    }

    public void setNsfwGroups(String nsfwGroups) {
        this.nsfwGroups = nsfwGroups;
    }
}
