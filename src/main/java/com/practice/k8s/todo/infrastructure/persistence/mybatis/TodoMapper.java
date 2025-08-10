package com.practice.k8s.todo.infrastructure.persistence.mybatis;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.practice.k8s.todo.domain.model.Todo;

@Mapper
public interface TodoMapper {
    Todo selectById(@Param("id") Long id);
    List<Todo> selectAll();
    int insert(Todo todo);
    int update(Todo todo);
    int deleteById(@Param("id") Long id);
} 