package com.practice.k8s.todo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.practice.k8s.todo.domain.model.Todo;

public interface TodoRepository {
    Todo save(Todo todo);
    Optional<Todo> findById(Long id);
    List<Todo> findAll();
    void deleteById(Long id);
}
 