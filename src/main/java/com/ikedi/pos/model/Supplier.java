package com.ikedi.pos.model;

import com.ikedi.pos.exception.ValidationException;

/** Someone we buy stock from. Products refer to a supplier by id. */
public class Supplier {

    private final String id;
    private String name;
    private String phone;
    private String email;

    public Supplier(String id, String name, String phone, String email) {
        this.id = requireText(id, "Supplier id");
        this.name = requireText(name, "Supplier name");
        this.phone = phone == null ? "" : phone.trim();
        this.email = email == null ? "" : email.trim();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }

    public void setName(String name) { this.name = requireText(name, "Supplier name"); }
    public void setPhone(String phone) { this.phone = phone == null ? "" : phone.trim(); }
    public void setEmail(String email) { this.email = email == null ? "" : email.trim(); }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required");
        }
        return value.trim();
    }
}

