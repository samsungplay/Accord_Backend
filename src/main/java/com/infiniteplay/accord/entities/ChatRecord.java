package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infiniteplay.accord.utils.TimeUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name="chat_record",indexes={
        @Index(columnList = "chatroom_id"),
        @Index(columnList = "reply_target_id"),
        @Index(columnList = "chatroom_id, type"),
        @Index(columnList = "sender_id"),
        @Index(columnList = "date"),
        @Index(columnList = "poll"),
        @Index(columnList = "reply_target_sender_id"),

})
public class ChatRecord extends BaseEntity {

    public ChatRecord() {

    }
    public ChatRecord(Integer id, String type, String message, ZonedDateTime date) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.date = date;
    }



    @GeneratedValue(strategy= GenerationType.AUTO)
    @Id
    private Integer id;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String type;

    private ZonedDateTime date;

    private boolean edited = false;

    @ManyToOne
    @JoinColumn(name="sender_id")
//    @JsonIgnore
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name="chatroom_id")
    private ChatRoom chatRoom;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "chatRecord")
    private List<ChatReaction> chatReactions = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "record")
    private List<Vote> pollVotes = new ArrayList<>();


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="reply_target_sender_id")
    private User replyTargetSender;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="poll",referencedColumnName = "id")
    private Poll poll;

    @Column(name="reply_target_message")
    private String replyTargetMessage;

    @Column(name="reply_target_id")
    private Integer replyTargetId;

    @Column(nullable = true)
    private String attachmentsMetadata;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String attachments;

    @Column(name="pinned",nullable = false)
    private Boolean pinned = false;

    @JsonIgnore
    @Column(name="pinned_date",nullable = false)
    private ZonedDateTime pinnedDate = TimeUtils.getCurrentKST();

    @Column(nullable = true)
    private Boolean hideEmbed;

    @Column(nullable = false)
    @JsonProperty("isNsfw")
    private Boolean isNsfw = false;

    @Column(nullable = false)
    @JsonProperty("isSpam")
    private Boolean isSpam = false;

    @JsonProperty("chatRoomIdRef")
    @Column(nullable = true)
    private Integer chatRoomIdReference;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public List<ChatReaction> getChatReactions() {
        return chatReactions;
    }

    public void setChatReactions(List<ChatReaction> chatReactions) {
        this.chatReactions = chatReactions;
    }

    public List<Vote> getPollVotes() {
        return pollVotes;
    }

    public void setPollVotes(List<Vote> pollVotes) {
        this.pollVotes = pollVotes;
    }

    public User getReplyTargetSender() {
        return replyTargetSender;
    }

    public void setReplyTargetSender(User replyTargetSender) {
        this.replyTargetSender = replyTargetSender;
    }

    public Poll getPoll() {
        return poll;
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
    }

    public String getReplyTargetMessage() {
        return replyTargetMessage;
    }

    public void setReplyTargetMessage(String replyTargetMessage) {
        this.replyTargetMessage = replyTargetMessage;
    }

    public Integer getReplyTargetId() {
        return replyTargetId;
    }

    public void setReplyTargetId(Integer replyTargetId) {
        this.replyTargetId = replyTargetId;
    }

    public String getAttachmentsMetadata() {
        return attachmentsMetadata;
    }

    public void setAttachmentsMetadata(String attachmentsMetadata) {
        this.attachmentsMetadata = attachmentsMetadata;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public ZonedDateTime getPinnedDate() {
        return pinnedDate;
    }

    public void setPinnedDate(ZonedDateTime pinnedDate) {
        this.pinnedDate = pinnedDate;
    }

    public Boolean getHideEmbed() {
        return hideEmbed;
    }

    public void setHideEmbed(Boolean hideEmbed) {
        this.hideEmbed = hideEmbed;
    }

    public Integer getChatRoomIdReference() {
        return chatRoomIdReference;
    }

    public void setChatRoomIdReference(Integer chatRoomIdReference) {
        this.chatRoomIdReference = chatRoomIdReference;
    }

    public Boolean getNsfw() {
        return isNsfw;
    }

    public void setNsfw(Boolean nsfw) {
        isNsfw = nsfw;
    }

    public Boolean getSpam() {
        return isSpam;
    }

    public void setSpam(Boolean spam) {
        isSpam = spam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ChatRecord other = (ChatRecord) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
