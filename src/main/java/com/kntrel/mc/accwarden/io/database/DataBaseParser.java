package com.kntrel.mc.accwarden.io.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public interface DataBaseParser<T extends Enitty> {

    T toEntity(ResultSet src) throws SQLException;

    Map<String, Object> toRow(T src);

}
