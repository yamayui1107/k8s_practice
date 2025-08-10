package com.practice.k8s.todo.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class TodoRequest {
    @NotBlank
    private String title;
    private String description;
    private LocalDate dueDate;
} 