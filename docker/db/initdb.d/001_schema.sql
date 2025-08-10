-- init schema for todo_db
CREATE DATABASE IF NOT EXISTS todo_db;
USE todo_db;

CREATE TABLE IF NOT EXISTS todos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    due_date DATE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
); 