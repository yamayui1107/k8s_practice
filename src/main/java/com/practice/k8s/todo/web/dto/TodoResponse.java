package com.practice.k8s.todo.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.practice.k8s.todo.domain.model.Todo;
import com.practice.k8s.todo.domain.model.TodoStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TodoResponse {
    Long id;
    String title;
    String description;
    TodoStatus status;
    LocalDate dueDate;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static TodoResponse from(Todo todo) {
        return TodoResponse.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .status(todo.getStatus())
                .dueDate(todo.getDueDate())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
} 