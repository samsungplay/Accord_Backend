package com.infiniteplay.accord.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
public abstract class BaseEntity {
    @Version
    @JsonIgnore
    private Long version;

    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }
}
