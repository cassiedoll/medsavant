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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ut.biolab.medsavant.util.BinaryConditionMS;


/**
 *
 * @author mfiume
 */
public class TableSchema implements Serializable {
    private static final Log LOG = LogFactory.getLog(TableSchema.class);

    private final LinkedHashMap<String,String> dbNameToAlias;
    private final LinkedHashMap<Integer,String> indexToDBName;
    private final LinkedHashMap<Integer,ColumnType> indexToColumnType;
    private final LinkedHashMap<DbColumn,Integer> columnToIndex;
    private final LinkedHashMap<String,DbColumn> aliasToColumn;
    private final LinkedHashMap<Integer,DbColumn> indexToColumn;
    private final LinkedHashMap<String,Integer> dbNameToIndex;
    private final LinkedHashMap<String, String> aliasToDBName;

    private final DbTable table;
    protected SelectQuery selectQuery;

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

    public TableSchema(DbSchema s, String name, ColumnDef... cols) {
        this(s.addTable(name));
        for (ColumnDef col: cols) {
            DbColumn dbc = addColumn(col.name, col.name, col.type, col.length);
            if (col.defaultValue != null) {
                dbc.setDefaultValue(col.defaultValue);
            }
            if (col.notNull) {
                dbc.notNull();
            }
            if (col.primaryKey) {
                dbc.primaryKey();
            }
        }
    }

    /**
     * Define a table-scheme using an interface class which provides all the column defs as members.
     * @param s the database schema
     * @param name name for the new table
     * @param columnsClass an interface which defines the column defs as static members
     */
    public TableSchema(DbSchema s, String name, Class columnsClass) {
        this(s.addTable(name));
        Field[] fields = columnsClass.getDeclaredFields();
        for (Field f: fields) {
            try {
                ColumnDef col = (ColumnDef)f.get(null);
                DbColumn dbc = addColumn(col.name, col.name, col.type, col.length);
                if (col.defaultValue != null) {
                    dbc.setDefaultValue(col.defaultValue);
                }
                if (col.notNull) {
                    dbc.notNull();
                }
                if (col.primaryKey) {
                    dbc.primaryKey();
                }
            } catch (Exception ex) {
                LOG.error("Unable to get column definition for " + f, ex);
            }
        }
    }

    private DbColumn addColumn(int index, String dbName, String alias, ColumnType t, int length) {
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
        return c;
    }

    public final DbColumn addColumn(String dbName, String alias, ColumnType t, int length) {
        return addColumn(getNumFields() + 1,dbName,alias,t,length);
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
    
    public DbColumn getDBColumn(int index) {
        return table.getColumns().get(index);
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

    public List<DbColumn> getColumns() {
        // IMPORTANT: this assumes the values returned are in order of insert (LinkedHashMap)
        return new ArrayList<DbColumn>(indexToColumn.values());
    }
    
    public DbTable getTable() {
        return table;
    }

    public String getTableName() {
        return table.getName();
    }
    
    public CreateTableQuery getCreateQuery() {
        return new CreateTableQuery(table, true);
    }
 
    /**
     * Create a query object for retrieving the given columns.  The query can be modified using the where() and distinct() modifiers.
     * 
     * @param cols list of columns whose values are to be fetched
     * @return a selection query
     */
    public synchronized SelectQuery select(Object... cols) {
        if (selectQuery == null) {
            selectQuery = new SelectQuery(false);
            selectQuery.addFromTable(table);
        }
        for (Object o: cols) {
            if (o instanceof ColumnDef) {
                selectQuery.addColumns(table.findColumn(((ColumnDef)o).name));
            } else {
                selectQuery.addCustomColumns(new CustomSql(o));
            }
        }
        SelectQuery result = selectQuery;
        selectQuery = null;
        return result;
    }

    /**
     * Create a query object which will match the given values of the given columns.
     *
     * @param wheres pairs consisting of column def followed by value
     * @return <code>this</code>
     */
    public synchronized TableSchema where(Object... wheres) {
        if (selectQuery == null) {
            selectQuery = new SelectQuery(false);
            selectQuery.addFromTable(table);
        }
        for (int i = 0; i < wheres.length; i += 2) {
            selectQuery.addCondition(BinaryConditionMS.equalTo(table.findColumn(((ColumnDef)wheres[i]).name), wheres[i + 1]));
        }
        return this;
    }
    
    /**
     * Make the query only fetch unique values.
     */
    public synchronized TableSchema distinct() {
        if (selectQuery != null) {
            selectQuery.setIsDistinct(true);
        } else {
            selectQuery = new SelectQuery(true);
            selectQuery.addFromTable(table);
        }
        return this;
    }
    
    /**
     * Add a GROUP BY clause to the query
     */
    public synchronized TableSchema groupBy(ColumnDef... groupCols) {
        if (selectQuery == null) {
            selectQuery = new SelectQuery(false);
            selectQuery.addFromTable(table);
        }
        for (ColumnDef col: groupCols) {
            selectQuery.addGroupings(table.findColumn(col.name));
        }
        return this;
    }
}
