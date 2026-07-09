package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_spaces")
public class StudySpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String building;

    @Column(nullable = false)
    private Integer floor;

    @Column(nullable = false)
    private String room;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private String defaultNoiseLevel; // silent, quiet, moderate, lively

    @Column(nullable = false)
    private String status = "EMPTY"; // EMPTY, MODERATE, CROWDED

    private LocalDateTime createdAt = LocalDateTime.now();

    public StudySpace() {}

    public StudySpace(String name, String building, Integer floor, String room, Integer capacity, String defaultNoiseLevel) {
        this.name = name;
        this.building = building;
        this.floor = floor;
        this.room = room;
        this.capacity = capacity;
        this.defaultNoiseLevel = defaultNoiseLevel;
        this.status = "EMPTY";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getDefaultNoiseLevel() {
        return defaultNoiseLevel;
    }

    public void setDefaultNoiseLevel(String defaultNoiseLevel) {
        this.defaultNoiseLevel = defaultNoiseLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
