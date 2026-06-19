package com.hyundai.test.address.persistence;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomerDataLifecycle {

    private final CsvCustomerStore store;
    private final boolean enabled;

    public CustomerDataLifecycle(
            CsvCustomerStore store,
            @Value("${address.persistence.enabled:true}") boolean enabled
    ) {
        this.store = store;
        this.enabled = enabled;
    }

    @PostConstruct
    public void load() {
        if (enabled) {
            store.load();
        }
    }

    @PreDestroy
    public void save() {
        if (enabled) {
            store.save();
        }
    }
}
