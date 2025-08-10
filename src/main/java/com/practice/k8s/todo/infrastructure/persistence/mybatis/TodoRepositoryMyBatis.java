package com.practice.k8s.todo.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.practice.k8s.todo.domain.model.Todo;
import com.practice.k8s.todo.domain.repository.TodoRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TodoRepositoryMyBatis implements TodoRepository {

    private final TodoMapper todoMapper;

    @Override
    public Todo save(Todo todo) {
        if (todo.getId() == null) {
            todoMapper.insert(todo);
        } else {
            todoMapper.update(todo);
        }
        return todo;
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return Optional.ofNullable(todoMapper.selectById(id));
    }

    @Override
    public List<Todo> findAll() {
        return todoMapper.selectAll();
    }

    @Override
    public void deleteById(Long id) {
        todoMapper.deleteById(id);
    }
} 