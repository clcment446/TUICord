package com.c446.disctui_server.db;

import java.util.List;
import java.util.Optional;

public interface CrudRepository<T, ID> {
    // Create & Update
    void save(T entity) throws Exception;
    Optional<T> findById(ID id) throws Exception;

    List<T> findAll() throws Exception;

    // Delete
    void deleteById(ID id) throws Exception;
}