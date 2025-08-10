package com.practice.k8s.todo.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Todo {
    private Long id;
    private String title;
    private String description;
    private TodoStatus status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Todo(String title, String description, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.status = TodoStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void complete() {
        if (this.status != TodoStatus.DONE) {
            this.status = TodoStatus.DONE;
            touch();
        }
    }

    public void update(String title, String description, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
} 