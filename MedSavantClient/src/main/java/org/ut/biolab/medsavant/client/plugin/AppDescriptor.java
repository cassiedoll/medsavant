/*
 *    Copyright 2011 University of Toronto
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
package org.ut.biolab.medsavant.client.plugin;

import java.io.File;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
/**
 * Plugin description read from the plugin.xml file.
 *
 * @author tarkvara
 */
public class AppDescriptor implements Comparable<AppDescriptor> {
    private static final Log LOG = LogFactory.getLog(AppDescriptor.class);
    public static class AppVersion implements Comparable {

        private final int minorVersion;
        private final int majorVersion;
        private final int bugfixVersion;

        public AppVersion(String version) {
            String[] s = version.split("\\.", 0);            
            try {
                majorVersion = Integer.parseInt(s[0]);
                minorVersion = Integer.parseInt(s[1]);
                bugfixVersion = Integer.parseInt(s[2]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new AssertionError("Invalid App Version " + version + ". App Versions must be of the format <major>.<minor>.<bugfix>");
            }
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + this.minorVersion;
            hash = 59 * hash + this.majorVersion;
            hash = 59 * hash + this.bugfixVersion;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final AppVersion other = (AppVersion) obj;
            if (this.minorVersion != other.minorVersion) {
                return false;
            }
            if (this.majorVersion != other.majorVersion) {
                return false;
            }
            if (this.bugfixVersion != other.bugfixVersion) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(Object obj) {
            if (obj == null) {
                return -1;
            }
            if (getClass() != obj.getClass()) {
                return -1;
            }
            final AppVersion other = (AppVersion) obj;

            if (this.majorVersion != other.majorVersion) {
                return new Integer(this.majorVersion).compareTo(new Integer(other.majorVersion));
            }
            if (this.minorVersion != other.minorVersion) {
                return new Integer(this.minorVersion).compareTo(new Integer(other.minorVersion));
            }
            if (this.bugfixVersion != other.bugfixVersion) {
                return new Integer(this.bugfixVersion).compareTo(new Integer(other.bugfixVersion));
            }
            return 0;
        }

        public boolean isNewerThan(AppVersion version) {
            return this.compareTo(version) > 0;
        }
    }
    /**
     * Bare-bones set of tags we need to recognise in plugin.xml in order to
     * identify plugins.
     */
    private enum PluginXMLElement {

        PLUGIN,
        ATTRIBUTE,
        PARAMETER,
        PROPERTY,
        IGNORED
    };

    /**
     * Bare-bones set of attributes we need to recognise in plugin.xml in order
     * to identify plugins.
     */
    private enum PluginXMLAttribute {

        ID,
        NAME,
        VALUE,
        VERSION,
        CLASS,
        CATEGORY,
        IGNORED
    };

    public enum Category {

        VISUALIZATION{
            @Override
                    public String toString(){
                return "Visualization";
            }
        },
        ANALYSIS{
            @Override
            public String toString(){
                return "Analysis and Statistics";
            }
        },
        SEARCH{
            @Override
            public String toString(){
                return "Search";
            }
        },
        UTILITY{
            @Override
            public String toString(){
                return "Utility";
            }
        },
        DATASOURCE{
            @Override
            public String toString(){
                return "External Datasource";
            }
        }
    };
    
    final String className;
    final String id;
    final String version;
    final String name;
    final String sdkVersion;
    final File file;
    final Category category;
    private static XMLStreamReader reader;

    private AppDescriptor(String className, String id, String version, String name, String sdkVersion, String category, File file) {
        this.className = className;
        this.id = id;
        this.version = version;
        this.name = name;
        this.sdkVersion = sdkVersion;
        this.category = Category.valueOf(category.toUpperCase());
        this.file = file;
    }

    @Override
    public String toString() {
        return id + "-" + version;
    }

    public String getClassName() {
        return className;
    }

    public String getID() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getSDKVersion() {
        return sdkVersion;
    }

    public Category getCategory() {
        return category;
    }

    public File getFile() {
        return file;
    }

    @Override
    public int compareTo(AppDescriptor t) {
        return (id + version).compareTo(t.id + t.version);
    }

    /**
     * Here's where we do our SDK compatibility check. Update this code whenever
     * the API changes.
     */
    public boolean isCompatible() {
        return sdkVersion.equals("1.0.0");
    }

    public static AppDescriptor fromFile(File f) throws PluginVersionException {
        try {
            JarFile jar = new JarFile(f);
            ZipEntry entry = jar.getEntry("plugin.xml");
            if (entry != null) {
                InputStream entryStream = jar.getInputStream(entry);
                reader = XMLInputFactory.newInstance().createXMLStreamReader(entryStream);
                String className = null;
                String id = null;
                String version = null;
                String sdkVersion = null;
                String name = null;
                String category = Category.UTILITY.toString();
                String currentElement = null;
                String currentText = "";
                do {
                    switch (reader.next()) {
                        case XMLStreamConstants.START_ELEMENT:
                            switch (readElement()) {
                                case PLUGIN:                                    
                                    className = readAttribute(PluginXMLAttribute.CLASS);
                                    id = readAttribute(PluginXMLAttribute.ID);
                                    version = readAttribute(PluginXMLAttribute.VERSION);                                                                        
                                    //category can be specified as an attribute or <property>.
                                    category = readAttribute(PluginXMLAttribute.CATEGORY);                                     
                                    break;
                                case ATTRIBUTE:
                                    if ("sdk-version".equals(readAttribute(PluginXMLAttribute.ID))) {
                                        sdkVersion = readAttribute(PluginXMLAttribute.VALUE);
                                    }
                                    break;
                                case PARAMETER:
                                    if ("name".equals(readAttribute(PluginXMLAttribute.ID))) {
                                        name = readAttribute(PluginXMLAttribute.VALUE);
                                    }
                                    break;
                                case PROPERTY:
                                    if ("name".equals(readAttribute(PluginXMLAttribute.NAME))) {
                                        name = readAttribute(PluginXMLAttribute.VALUE);
                                        if (name == null) {
                                            currentElement = "name";
                                        }
                                    }

                                    if ("sdk-version".equals(readAttribute(PluginXMLAttribute.NAME))) {
                                        sdkVersion = readAttribute(PluginXMLAttribute.VALUE);
                                        if (sdkVersion == null) {
                                            currentElement = "sdk-version";
                                        }
                                    }
                                    
                                    if ("category".equals(readAttribute(PluginXMLAttribute.NAME))) {
                                        category = readAttribute(PluginXMLAttribute.VALUE);                                        
                                        if (category == null) {
                                            currentElement = "category";
                                        }
                                    }
                                     
                                    break;
                            }
                            break;
                            
                        case XMLStreamConstants.CHARACTERS:
                            if (reader.isWhiteSpace()) {
                                break;
                            } else if (currentElement != null) {
                                currentText += reader.getText().trim().replace("\t", "");                                
                            }
                            break;

                        case XMLStreamConstants.END_ELEMENT:
                            if (readElement() == PluginXMLElement.PROPERTY) {
                                if (currentElement != null && currentText.length() > 0) {
                                    if (currentElement.equals("name")) {
                                        name = currentText;
                                    } else if (currentElement.equals("sdk-version")) {
                                        sdkVersion = currentText;
                                    }else if(currentElement.equals("category")){
                                        category = currentText;
                                    }
                                }
                                currentText = "";
                                currentElement = null;
                            }
                            break;

                        case XMLStreamConstants.END_DOCUMENT:
                            reader.close();
                            reader = null;
                            break;
                    }
                } while (reader != null);
                
                if (className != null && id != null && name != null) {
                    return new AppDescriptor(className, id, version, name, sdkVersion, category, f);
                }
            }
        } catch (Exception x) {
            LOG.error("Error parsing plugin.xml from "+f.getAbsolutePath()+": "+x);            
        }
        throw new PluginVersionException(f.getName() + " did not contain a valid plugin");
    }

    private static PluginXMLElement readElement() {
        try {
            String elemName = reader.getLocalName().toUpperCase();
            return Enum.valueOf(PluginXMLElement.class, elemName);
        } catch (IllegalArgumentException ignored) {
            // Any elements not in our enum will just be ignored.
            return PluginXMLElement.IGNORED;
        }
    }

    private static String readAttribute(PluginXMLAttribute attr) {
        return reader.getAttributeValue(null, attr.toString().toLowerCase());
    }    
}