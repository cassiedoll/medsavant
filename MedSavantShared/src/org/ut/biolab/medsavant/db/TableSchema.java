/*
 *    Copyright 2011-2012 University of Toronto
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.ut.biolab.medsavant.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;


/**
 *
 * @author mfiume
 */
public class TableSchema implements Serializable {

    private final LinkedHashMap<String,String> dbNameToAlias;
    private final LinkedHashMap<Integer,String> indexToDBName;
    private final LinkedHashMap<Integer,ColumnType> indexToColumnType;
    private final LinkedHashMap<DbColumn,Integer> columnToIndex;
    private final LinkedHashMap<String,DbColumn> aliasToColumn;
    private final LinkedHashMap<Integer,DbColumn> indexToColumn;
    private final LinkedHashMap<String,Integer> dbNameToIndex;
    private final LinkedHashMap<String, String> aliasToDBName;

    private void addColumn(int index, String dbName, String alias, ColumnType t, int length) {
        assert (!indexToDBName.containsKey(index));
        DbColumn c = table.addColumn(dbName, t.toString(), length);
        dbNameToAlias.put(dbName,alias);
        indexToDBName.put(index, dbName);
        aliasToColumn.put(alias, c);
        columnToIndex.put(c,index);
        indexToColumnType.put(index, t);
        indexToColumn.put(index, c);
        dbNameToIndex.put(dbName, index);
        aliasToDBName.put(alias,dbName);
    }

    public void addColumn(String dbName, String alias, ColumnType t, int length) {
        addColumn(getNumFields()+1,dbName,alias,t,length);
    }

    public List<String> getFieldAliases() {
        return new ArrayList<String>(this.aliasToDBName.keySet());
    }

    public int getNumFields() {
        return indexToDBName.keySet().size();
    }

    public ColumnType getColumnType(int index) {
        assert (indexToColumnType.containsKey(index));
        return indexToColumnType.get(index);
    }

    public int getColumnIndex(DbColumn c) {
        assert (columnToIndex.containsKey(c));
        return columnToIndex.get(c);
    }

    public ColumnType getColumnType(DbColumn c) {
        return this.getColumnType(this.getColumnIndex(c));
    }

    public DbColumn getDBColumnByAlias(String alias) {
        assert (aliasToColumn.containsKey(alias));
        return aliasToColumn.get(alias);
    }
    
    public DbColumn getDBColumn(String columnname) {
        assert (dbNameToAlias.containsKey(columnname));
        return getDBColumnByAlias(dbNameToAlias.get(columnname));
    }
    
    public int getFieldIndexInDB(String dbName) {
        assert (dbNameToIndex.containsKey(dbName));
        return dbNameToIndex.get(dbName);
    }
    
    public int getFieldIndexByAlias(String alias) {
        return getFieldIndexInDB(getDBName(alias));
    }

    public String getFieldAlias(String dbName) {
        assert (dbNameToAlias.containsKey(dbName));
        return dbNameToAlias.get(dbName);
    }

    public String getDBName(String alias) {
        assert (aliasToDBName.containsKey(alias));
        return aliasToDBName.get(alias);
    }

    public String getDBName(int index) {
        assert (indexToDBName.containsKey(index));
        return indexToDBName.get(index);
    }

    private DbTable table;

    public TableSchema(DbTable t) {
        this.table = t;
        
        dbNameToAlias = new LinkedHashMap<String,String>();
        indexToDBName = new LinkedHashMap<Integer,String>();
        indexToColumnType = new LinkedHashMap<Integer,ColumnType>();
        columnToIndex = new LinkedHashMap<DbColumn,Integer>();
        aliasToColumn = new LinkedHashMap<String,DbColumn>();
        indexToColumn = new LinkedHashMap<Integer,DbColumn>();
        dbNameToIndex = new LinkedHashMap<String,Integer>();
        aliasToDBName = new LinkedHashMap<String,String>();
    }

    public DbTable getTable() {
        return table;
    }

    public List<DbColumn> getColumns() {
        // IMPORTANT: this assumes the values returned are in order of insert (LinkedHashMap)
        return new ArrayList<DbColumn>(indexToColumn.values());
    }
    
    public String getTablename() {
        return table.getName();
    }
}
