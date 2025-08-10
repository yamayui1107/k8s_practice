package com.practice.k8s.todo.application;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.practice.k8s.todo.domain.model.Todo;
import com.practice.k8s.todo.domain.repository.TodoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TodoApplicationService {

    private final TodoRepository todoRepository;

    public Todo create(String title, String description, LocalDate dueDate) {
        Todo todo = new Todo(title, description, dueDate);
        return todoRepository.save(todo);
    }

    @Transactional(readOnly = true)
    public List<Todo> list() {
        return todoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Todo get(Long id) {
        return todoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Todo not found: " + id));
    }

    public Todo update(Long id, String title, String description, LocalDate dueDate) {
        Todo todo = get(id);
        todo.update(title, description, dueDate);
        return todoRepository.save(todo);
    }

    public Todo complete(Long id) {
        Todo todo = get(id);
        todo.complete();
        return todoRepository.save(todo);
    }

    public void delete(Long id) {
        todoRepository.deleteById(id);
    }
} 