package com.money.contract.goods;

/** A brand ID/name pair without exposing the GMS persistence entity. */
public class BrandSelectionSnapshot {

    private final Long id;
    private final String name;

    public BrandSelectionSnapshot(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
