package com.gymlet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Simple key/value store for app-level flags (e.g. whether demo data was seeded). */
@Entity
@Table(name = "app_meta")
public class AppMeta {

    @Id
    @Column(nullable = false)
    private String metaKey;

    @Column(nullable = false)
    private String metaValue;

    public AppMeta() {
    }

    public AppMeta(String metaKey, String metaValue) {
        this.metaKey = metaKey;
        this.metaValue = metaValue;
    }

    public String getMetaKey() {
        return metaKey;
    }

    public void setMetaKey(String metaKey) {
        this.metaKey = metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }

    public void setMetaValue(String metaValue) {
        this.metaValue = metaValue;
    }
}
