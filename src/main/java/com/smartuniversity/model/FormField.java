package com.smartuniversity.model;

import jakarta.persistence.*;

@Entity
@Table(name = "form_fields")
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long competitionId;

    @Column(nullable = false)
    private String fieldLabel; // "Full Name", "Email", "Phone Number", etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "field_order")
    private Integer order; // For ordering fields in the form

    @Column(length = 1000)
    private String options; // JSON string for dropdown/checkbox options, e.g., "[\"Option 1\", \"Option 2\"]"

    @Column(length = 500)
    private String placeholder;

    public enum FieldType {
        TEXT,
        EMAIL,
        PHONE,
        NUMBER,
        TEXTAREA,
        DROPDOWN,
        CHECKBOX,
        DATE
    }

    // Constructors
    public FormField() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(Long competitionId) {
        this.competitionId = competitionId;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }
}
