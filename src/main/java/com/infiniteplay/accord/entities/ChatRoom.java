package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infiniteplay.accord.utils.JacksonLazyFieldsFilter;
import com.infiniteplay.accord.utils.TimeUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

@Entity
@Table(name = "accord_chatroom", indexes = {
        @Index(columnList = "recent_message_date"),
})
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "owner_id")
    private Integer ownerId;

    @SuppressWarnings("JpaAttributeTypeInspection")
    @Column(name = "mod_id", columnDefinition = "integer[]", nullable = true)
    private Integer[] modIds;

    String name;

    @Column(nullable = true, unique = true)
    String direct1to1Identifier;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "chatRooms")
    @Fetch(FetchMode.SUBSELECT)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = JacksonLazyFieldsFilter.class)
    Set<User> participants = new LinkedHashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "chatRoom")
    @Fetch(FetchMode.SUBSELECT)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = JacksonLazyFieldsFilter.class)
    @OrderBy("orderId ASC, id ASC")
    List<Sound> sounds = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "chatRoom")
    @Fetch(FetchMode.SUBSELECT)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = JacksonLazyFieldsFilter.class)
    List<Background> backgrounds = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "chatRoom")
    @JsonIgnore
    List<ChatRecord> chatRecords;

    @Column(nullable = false, name = "recent_message_date")
    ZonedDateTime recentMessageDate = TimeUtils.getCurrentKST();

    @Transient
    Integer notificationCount = 0;

    @Transient
    Integer latestMessageId = 0;

    @OneToOne(mappedBy = "chatRoom", fetch = FetchType.EAGER)
    Call callInstance;

    @Column(nullable = true)
    String roomImage;

    @Column(nullable = false, name = "is_public")
    @JsonProperty("isPublic")
    Boolean isPublic = false;

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }

    public String getRoomImage() {
        return roomImage;
    }

    public void setRoomImage(String roomImage) {
        this.roomImage = roomImage;
    }

    public Integer[] getModIds() {
        return modIds;
    }

    public void setModIds(Integer[] modIds) {
        this.modIds = modIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ChatRoom other = (ChatRoom) o;
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

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirect1to1Identifier() {
        return direct1to1Identifier;
    }

    public void setDirect1to1Identifier(String direct1to1Identifier) {
        this.direct1to1Identifier = direct1to1Identifier;
    }

    public Set<User> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<User> participants) {
        this.participants = participants;
    }

    public List<ChatRecord> getChatRecords() {
        return chatRecords;
    }

    public void setChatRecords(List<ChatRecord> chatRecords) {
        this.chatRecords = chatRecords;
    }

    public ZonedDateTime getRecentMessageDate() {
        return recentMessageDate;
    }

    public void setRecentMessageDate(ZonedDateTime recentMessageDate) {
        this.recentMessageDate = recentMessageDate;
    }

    public Integer getNotificationCount() {
        return notificationCount;
    }

    public void setNotificationCount(Integer notificationCount) {
        this.notificationCount = notificationCount;
    }

    public Integer getLatestMessageId() {
        return latestMessageId;
    }

    public void setLatestMessageId(Integer latestMessageId) {
        this.latestMessageId = latestMessageId;
    }

    public Call getCallInstance() {
        return callInstance;
    }

    public void setCallInstance(Call callInstance) {
        this.callInstance = callInstance;
    }

    public List<Sound> getSounds() {
        return sounds;
    }

    public void setSounds(List<Sound> sounds) {
        this.sounds = sounds;
    }

    public List<Background> getBackgrounds() {
        return backgrounds;
    }

    public void setBackgrounds(List<Background> backgrounds) {
        this.backgrounds = backgrounds;
    }
}
