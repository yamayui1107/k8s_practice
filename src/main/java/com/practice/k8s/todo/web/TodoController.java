package com.practice.k8s.todo.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.practice.k8s.todo.application.TodoApplicationService;
import com.practice.k8s.todo.domain.model.Todo;
import com.practice.k8s.todo.web.dto.TodoRequest;
import com.practice.k8s.todo.web.dto.TodoResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoApplicationService service;

    @GetMapping
    public List<TodoResponse> list() {
        return service.list().stream().map(TodoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TodoResponse get(@PathVariable Long id) {
        Todo todo = service.get(id);
        return TodoResponse.from(todo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@Valid @RequestBody TodoRequest req) {
        Todo todo = service.create(req.getTitle(), req.getDescription(), req.getDueDate());
        return TodoResponse.from(todo);
    }

    @PutMapping("/{id}")
    public TodoResponse update(@PathVariable Long id, @Valid @RequestBody TodoRequest req) {
        Todo todo = service.update(id, req.getTitle(), req.getDescription(), req.getDueDate());
        return TodoResponse.from(todo);
    }

    @PostMapping("/{id}/complete")
    public TodoResponse complete(@PathVariable Long id) {
        Todo todo = service.complete(id);
        return TodoResponse.from(todo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
} 