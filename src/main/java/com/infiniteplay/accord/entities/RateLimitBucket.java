package com.infiniteplay.accord.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

@Entity
public class RateLimitBucket extends BaseEntity {

    public RateLimitBucket() {
    }

    public RateLimitBucket(Integer id, String bucketId, byte[] bucketData) {
        this.id = id;
        this.bucketId = bucketId;
        this.bucketData = bucketData;
    }



    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(unique=true)
    String bucketId;

    @Lob
    @Column(columnDefinition = "bigint")
    byte[] bucketData;


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public byte[] getBucketData() {
        return bucketData;
    }

    public void setBucketData(byte[] bucketData) {
        this.bucketData = bucketData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        RateLimitBucket other = (RateLimitBucket) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
