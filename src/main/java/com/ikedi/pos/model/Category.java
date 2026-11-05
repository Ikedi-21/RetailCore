package com.ikedi.pos.model;

import com.ikedi.pos.exception.ValidationException;

/** A product category. Products refer to it by name. */
public class Category {

    private final String name;
    private String description;

    public Category(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Category name is required");
        }
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description.trim(); }
}

