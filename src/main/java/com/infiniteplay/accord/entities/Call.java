package com.infiniteplay.accord.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infiniteplay.accord.utils.JacksonLazyFieldsFilter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name="call", indexes = {
        @Index(columnList = "chatroom")
})
public class Call extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "activeCallInstance")
    @Fetch(FetchMode.SUBSELECT)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = JacksonLazyFieldsFilter.class)
    private List<User> activeParticipants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "pendingCallInstances")
    @Fetch(FetchMode.SUBSELECT)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = JacksonLazyFieldsFilter.class)
    private Set<User> pendingParticipants = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="chatroom")
    @JsonIgnore
    @Fetch(FetchMode.JOIN)
    public ChatRoom chatRoom;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = true)
    @JsonProperty("hasMusic")
    private boolean hasMusic;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Call other = (Call) o;
        System.out.println(id != null && id.equals(other.id));
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "call#"+id;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<User> getActiveParticipants() {
        return activeParticipants;
    }

    public void setActiveParticipants(List<User> activeParticipants) {
        this.activeParticipants = activeParticipants;
    }

    public Set<User> getPendingParticipants() {
        return pendingParticipants;
    }

    public void setPendingParticipants(Set<User> pendingParticipants) {
        this.pendingParticipants = pendingParticipants;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public boolean hasMusic() {
        return hasMusic;
    }

    public void setHasMusic(boolean hasMusic) {
        this.hasMusic = hasMusic;
    }
}
