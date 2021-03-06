package org.umlg.sqlg.structure.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.TopologyInf;
import org.umlg.sqlg.util.ThreadLocalSet;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date: 2016/09/04
 * Time: 8:50 AM
 */
public class PropertyColumn implements TopologyInf {

    private final AbstractLabel abstractLabel;
    private final String name;
    private boolean committed = true;
    private final PropertyType propertyType;
    private final Set<GlobalUniqueIndex> globalUniqueIndices = ConcurrentHashMap.newKeySet();
    private final Set<GlobalUniqueIndex> uncommittedGlobalUniqueIndices = new ThreadLocalSet<>();

    PropertyColumn(AbstractLabel abstractLabel, String name, PropertyType propertyType) {
        this.abstractLabel = abstractLabel;
        this.name = name;
        this.propertyType = propertyType;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean isCommitted() {
        return this.committed;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public AbstractLabel getParentLabel() {
        return abstractLabel;
    }

    void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public Set<GlobalUniqueIndex> getGlobalUniqueIndices() {
        HashSet<GlobalUniqueIndex> result = new HashSet<>();
        result.addAll(this.globalUniqueIndices);
        if (this.abstractLabel.getSchema().getTopology().isSchemaChanged()) {
            result.addAll(this.uncommittedGlobalUniqueIndices);
        }
        return result;
    }

    void afterCommit() {
        Preconditions.checkState(this.getParentLabel().getTopology().isSchemaChanged(), "PropertyColumn.afterCommit must have schemaChanged = true");
        Iterator<GlobalUniqueIndex> globalUniqueIndexIter = this.uncommittedGlobalUniqueIndices.iterator();
        while (globalUniqueIndexIter.hasNext()) {
            GlobalUniqueIndex globalUniqueIndex = globalUniqueIndexIter.next();
            this.globalUniqueIndices.add(globalUniqueIndex);
            globalUniqueIndexIter.remove();
        }
        this.committed = true;
    }

    void afterRollback() {
        Preconditions.checkState(this.getParentLabel().getTopology().isSchemaChanged(), "PropertyColumn.afterRollback must have schemaChanged = true");
        this.uncommittedGlobalUniqueIndices.clear();
    }

    /**
     * Only called from {@link Topology#fromNotifyJson(int, LocalDateTime)}
     *
     * @param globalUniqueIndex The {@link GlobalUniqueIndex} to add.
     */
    void addToGlobalUniqueIndexes(GlobalUniqueIndex globalUniqueIndex) {
        this.globalUniqueIndices.add(globalUniqueIndex);
        this.abstractLabel.addGlobalUniqueIndexToProperties(this);
    }

    void addGlobalUniqueIndex(GlobalUniqueIndex globalUniqueIndex) {
        this.uncommittedGlobalUniqueIndices.add(globalUniqueIndex);
        this.abstractLabel.addGlobalUniqueIndexToUncommittedProperties(this);
    }

    ObjectNode toNotifyJson() {
        ObjectNode propertyObjectNode = new ObjectNode(Topology.OBJECT_MAPPER.getNodeFactory());
        propertyObjectNode.put("name", this.name);
        propertyObjectNode.put("propertyType", this.propertyType.name());
        return propertyObjectNode;
    }

    static PropertyColumn fromNotifyJson(AbstractLabel abstractLabel, JsonNode jsonNode) {
       String pt = jsonNode.get("propertyType").asText();
       if (pt.equals("VARCHAR")) {
           //This is not ideal, however Sqlg only uses VARCHAR when creating the column.
           //For the rest is is considered the same as STRING
           return new PropertyColumn(
                   abstractLabel,
                   jsonNode.get("name").asText(),
                   PropertyType.STRING);
       } else {
           return new PropertyColumn(
                   abstractLabel,
                   jsonNode.get("name").asText(),
                   PropertyType.valueOf(pt));
       }
    }

    @Override
    public int hashCode() {
        return (this.abstractLabel.getSchema().getName() + this.abstractLabel.getLabel() + this.getName()).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof PropertyColumn)) {
            return false;
        }
        PropertyColumn other = (PropertyColumn) o;
        if (this.abstractLabel.getSchema().getName().equals(other.abstractLabel.getSchema().getName())) {
            if (this.abstractLabel.getLabel().equals(other.abstractLabel.getLabel())) {
                return this.getName().equals(other.getName()) && this.getPropertyType() == other.getPropertyType();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return this.abstractLabel.getSchema().getName() + "." + this.abstractLabel.getLabel() + "." + this.name;
    }
    
    
    @Override
    public void remove(boolean preserveData) {
    	this.abstractLabel.removeProperty(this, preserveData);
    }
}
