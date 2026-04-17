package com.c446.disctui_server.db;

import java.sql.Connection;

public abstract class AbstractRepository<T, ID> implements CrudRepository<T, ID> {

    protected Connection getConnection() throws Exception {
        return DBMan.getConnection();
    }

    // You can implement common logic here or leave it
    // to specific implementations for complex SQL mapping.
}