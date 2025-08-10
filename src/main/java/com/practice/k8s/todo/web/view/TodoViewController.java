package com.practice.k8s.todo.web.view;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.practice.k8s.todo.application.TodoApplicationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoViewController {

    private final TodoApplicationService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("todos", service.list());
        model.addAttribute("form", new TodoForm());
        return "todos/index";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TodoForm form, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("todos", service.list());
            return "todos/index";
        }
        service.create(form.title(), form.description(), form.dueDate());
        return "redirect:/todos";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id) {
        service.complete(id);
        return "redirect:/todos";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/todos";
    }

    public record TodoForm(
            @jakarta.validation.constraints.NotBlank String title,
            String description,
            LocalDate dueDate
    ) {
        public TodoForm() { this("", null, null); }
    }
} 