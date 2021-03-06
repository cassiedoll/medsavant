/**
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.ut.biolab.medsavant.shared.vcf;

import java.io.Serializable;
import org.apache.commons.lang.NumberUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.ut.biolab.medsavant.shared.util.MiscUtils;

/**
 *
 * @author mfiume, AndrewBrook
 */
public class VariantRecord implements Serializable {

    public static enum VariantType {

        //BND => complex rearrangement
        SNP, Insertion, Deletion, Various, Unknown, InDel, Complex, HomoRef;

        public static VariantType getVariantType(int type) {
            switch (type) {
                case 0:
                    return SNP;
                case 1:
                    return Insertion;
                case 2:
                    return Deletion;
                case 3:
                    return Various;
                case 4:
                    return Unknown;
                case 5:
                    return InDel;
                case 6:
                    return Complex;
                case 7:
                    return HomoRef;
                default:
                    return Unknown;
            }
        }
    };

    public static enum Zygosity {

        HomoRef, HomoAlt, Hetero, HeteroTriallelic, Missing;

        public static Zygosity getZygosity(int zygosity) {
            switch (zygosity) {
                case 0:
                    return HomoRef;
                case 1:
                    return HomoAlt;
                case 2:
                    return Hetero;
                case 3:
                    return HeteroTriallelic;
                case 4:
                    return Missing;
                default:
                    return HomoRef;
            }
        }
    };
    private static final int FILE_INDEX_OF_CHROM = 0;
    private static final int FILE_INDEX_OF_POS = FILE_INDEX_OF_CHROM + 1;
    private static final int FILE_INDEX_OF_DBSNPID = FILE_INDEX_OF_POS + 1;
    private static final int FILE_INDEX_OF_REF = FILE_INDEX_OF_DBSNPID + 1;
    private static final int FILE_INDEX_OF_ALT = FILE_INDEX_OF_REF + 1;
    private static final int FILE_INDEX_OF_QUAL = FILE_INDEX_OF_ALT + 1;
    private static final int FILE_INDEX_OF_FILTER = FILE_INDEX_OF_QUAL + 1;
    private static final int FILE_INDEX_OF_INFO = FILE_INDEX_OF_FILTER + 1;
    public static final Class CLASS_OF_VARIANTID = Integer.class;
    public static final Class CLASS_OF_GENOMEID = Integer.class;
    public static final Class CLASS_OF_PIPELINEID = String.class;
    public static final Class CLASS_OF_DNAID = String.class;
    public static final Class CLASS_OF_CHROM = String.class;
    public static final Class CLASS_OF_POSITION = Long.class;
    public static final Class CLASS_OF_DBSNPID = String.class;
    public static final Class CLASS_OF_REF = String.class;
    public static final Class CLASS_OF_ALT = String.class;
    public static final Class CLASS_OF_QUAL = Float.class;
    public static final Class CLASS_OF_FILTER = String.class;
    public static final Class CLASS_OF_CUSTOMINFO = String.class;
    private int uploadID;
    private int fileID;
    private int variantID;
    private int genomeID;
    private int pipelineID;
    private String dnaID;
    private String chrom;
    private Long start_position;
    private Long end_position;
    private String dbSNPID;
    private String ref;
    private String alt;
    private int altNumber;
    private Float qual;
    private String filter;
    private VariantType type;
    private Zygosity zygosity;
    private String genotype;
    private String customInfo;
    private Object[] customFields;
    private String ancestralAllele;
    private Integer alleleCount;
    private Float alleleFrequency;
    private Integer numberOfAlleles;
    private Integer baseQuality;
    private String cigar;
    private Boolean dbSNPMembership;
    private Integer depthOfCoverage;
    // private Long endPosition;
    private Boolean hapmap2Membership;
    private Boolean hapmap3Membership;
    private Integer mappingQuality;
    private Integer numberOfZeroMQ;
    private Integer numberOfSamplesWithData;
    private Float strandBias;
    private Boolean isSomatic;
    private Boolean isValidated;
    private Boolean isInThousandGenomes;
    public static final String nullString = ".";

    /*
     * DO NOT USE THIS UNLESS YOU KNOW WHAT YOU'RE DOING
     * Useful for creating empty record in some circumstances
     */
    public VariantRecord() {
    }

    public VariantRecord(String[] line, long start, long end, String ref, String alt, int altNumber, VariantType vt) throws Exception {//, String[] infoKeys, Class[] infoClasses) {
        dnaID = null;
        chrom = (String) parse(CLASS_OF_CHROM, line[FILE_INDEX_OF_CHROM]);
        if (!chrom.toLowerCase().startsWith("chr")) {
            String s = MiscUtils.homogenizeSequence(chrom);
            chrom = "chr" + s;

            //Comment out chromosome check
            /*
            if (!s.equalsIgnoreCase("x") && !s.equalsIgnoreCase("y") && !s.equalsIgnoreCase("m")) {

                if (NumberUtils.isNumber(s)) {
                    int x = Integer.parseInt(s);
                    if (x < 1 || x > 22) {
                        throw new IllegalArgumentException("Invalid chromosome " + chrom) {
                            @Override
                            public Throwable fillInStackTrace() {
                                return this;
                            }
                        };
                    }
                }else{
                    throw new IllegalArgumentException("Invalid chromosome "+chrom){
                        @Override
                        public Throwable fillInStackTrace(){ return this; }
                    };
                }

            }*/
        }

        dbSNPID = (String) parse(CLASS_OF_DBSNPID, line[FILE_INDEX_OF_DBSNPID]);

        qual = (Float) parse(CLASS_OF_QUAL, line[FILE_INDEX_OF_QUAL]);
        filter = (String) parse(CLASS_OF_FILTER, line[FILE_INDEX_OF_FILTER]);
        customInfo = (String) parse(CLASS_OF_CUSTOMINFO, line[FILE_INDEX_OF_INFO]);
        
        this.start_position = start;
        this.end_position = end;
        this.alt = alt;
        this.altNumber = altNumber;
        this.ref = ref;
        this.type = vt;

        extractInfo(customInfo);
    }

    public VariantRecord(
            int uploadID,
            int fileID,
            int variantID,
            int genomeID,
            int pipelineID,
            String dnaID,
            String chrom,
            long start_position,
            long end_position,
            String dbSNPID,
            String ref,
            String alt,
            int altNumber,
            float qual,
            String filter,
            String customInfo,
            Object[] customFields) {
        this.uploadID = uploadID;
        this.fileID = fileID;
        this.variantID = variantID;
        this.genomeID = genomeID;
        this.pipelineID = pipelineID;
        this.dnaID = dnaID;
        this.chrom = chrom;
        this.start_position = start_position;
        this.end_position = end_position;
        this.dbSNPID = dbSNPID;
        this.ref = ref;
        this.alt = alt;
        this.altNumber = altNumber;
        this.qual = qual;
        this.filter = filter;
        this.setCustomInfo(customInfo);
        this.customFields = customFields;
    }

    public VariantRecord(VariantRecord r) {
        this.setVariantID(r.getVariantID());
        this.setDnaID(r.getDnaID());
        this.setGenomeID(r.getGenomeID());
        this.setPipelineID(r.getPipelineID());
        this.setChrom(r.getChrom());
        //this.setPosition(r.getPosition());
        this.setStartPosition(r.getStartPosition());
        this.setEndPosition(r.getEndPosition());
        this.setDbSNPID(r.getDbSNPID());
        this.setRef(r.getRef());
        this.setAlt(r.getAlt());
        this.setAltNumber(r.getAltNumber());
        this.setQual(r.getQual());
        this.setFilter(r.getFilter());
        this.setCustomInfo(r.getCustomInfo());
        this.setCustomFields(r.getCustomFields());
        this.setType(r.getType());
    }
    
    public void setAltNumber(int a){
        this.altNumber = a;
    }
    
    public int getAltNumber(){
        return this.altNumber;
    }

    private static Object parse(Class c, String value) {

        if (c == String.class) {
            if (value.equals(nullString)) {
                return "";
            }
            return value;
        }

        if (value.equals(nullString)) {
            return null;
        }

        if (c == Long.class) {
            try {
                return NumberUtils.isDigits(value) ? Long.parseLong(value) : null;
            } catch (Exception e) {
                return null;
            }
        }

        if (c == Float.class) {
            try {
                return NumberUtils.isNumber(value) ? Float.parseFloat(value) : null;
            } catch (Exception e) {
                return null;
            }
        }

        //if flag exists, set to true
        if (c == Boolean.class) {
            return true;
        }

        if (c == Integer.class) {
            try {
                return NumberUtils.isDigits(value) ? Integer.parseInt(value) : null;
            } catch (Exception e) {
                return null;
            }
        }

        throw new UnsupportedOperationException("Parser doesn't deal with objects of type " + c);
    }

    /*
    @Deprecated
    public VariantType getVariantType(String ref, String alt) {
        if (ref.startsWith("<") || alt.startsWith("<")) {
            if (alt.contains("<DEL>") || ref.contains("<DEL>")) {
                return VariantType.Deletion;
            } else if (alt.contains("<INS>") || ref.contains("<INS>")) {
                return VariantType.Insertion;
            } else {
                return VariantType.Unknown;
            }
        }

        VariantType result = null;
        for (String s : alt.split(",")) {
            if (ref == null || (s != null && s.length() > ref.length())) {
                result = variantTypeHelper(result, VariantType.Insertion);
            } else if (s == null || (ref != null && ref.length() > s.length())) {
                result = variantTypeHelper(result, VariantType.Deletion);
            } else {
                result = variantTypeHelper(result, VariantType.SNP);
            }
        }
        return result;
    }
*/
    private VariantType variantTypeHelper(VariantType currentType, VariantType newType) {
        if (currentType == null || currentType == newType) {
            return newType;
        } else {
            return VariantType.Various;
        }
    }

    public static Object[] parseInfo(String infoString, String[] infoKeys, Class[] infoClasses) {
        Object[] values = new Object[infoKeys.length];

        infoString = infoString.trim();
        String[] list = infoString.split(";");

        for (String element : list) {
            String name = element;
            String value = "";
            int equals = element.indexOf("=");
            if (equals != -1) {
                name = element.substring(0, equals);
                value = element.substring(equals + 1);
            }
            for (int i = 0; i < infoKeys.length; i++) {
                if (name.toLowerCase().equals(infoKeys[i].toLowerCase())) {
                    values[i] = parse(infoClasses[i], value);
                }
            }
        }

        return values;
    }

    public int getUploadID() {
        return uploadID;
    }

    public void setUploadIDID(int uploadID) {
        this.uploadID = uploadID;
    }

    public int getFileID() {
        return fileID;
    }

    public void setFileID(int fileID) {
        this.fileID = fileID;
    }

    public int getVariantID() {
        return variantID;
    }

    public void setVariantID(int variantID) {
        this.variantID = variantID;
    }

    public int getGenomeID() {
        return genomeID;
    }

    public void setGenomeID(int genomeID) {
        this.genomeID = genomeID;
    }

    public int getPipelineID() {
        return pipelineID;
    }

    public void setPipelineID(int pipelineID) {
        this.pipelineID = pipelineID;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public String getChrom() {
        return chrom;
    }

    public void setChrom(String chrom) {
        this.chrom = chrom;
    }

    public String getDbSNPID() {
        return dbSNPID;
    }

    public void setDbSNPID(String id) {
        this.dbSNPID = id;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Long getStartPosition() {
        return start_position;
    }

    public void setStartPosition(Long pos) {
        this.start_position = pos;
    }

    public Long getEndPosition() {
        return end_position;
    }

    public void setEndPosition(Long pos) {
        this.end_position = pos;
    }

    public Float getQual() {
        return qual;
    }

    public void setQual(Float qual) {
        this.qual = qual;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getDnaID() {
        return dnaID;
    }

    public void setDnaID(String dnaID) {
        this.dnaID = dnaID;
    }

    public String getCustomInfo() {
        return customInfo;
    }

    public void setCustomInfo(String customInfo) {
        this.customInfo = customInfo;
        extractInfo(customInfo);
    }

    public Object[] getCustomFields() {
        return customFields;
    }

    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
    }

    public Zygosity getZygosity() {
        return zygosity;
    }

    public void setZygosity(Zygosity zygosity) {
        this.zygosity = zygosity;
    }

    public String getGenotype() {
        return genotype;
    }

    public void setGenotype(String genotype) {
        this.genotype = genotype;
    }

    public void setCustomFields(Object[] customFields) {
        this.customFields = customFields;
    }

    public int compareTo(VariantRecord other) {
        return compareTo(other.getChrom(), other.getStartPosition(), other.getEndPosition());
    }

    public int compareTo(String chrom, long startpos, long endpos) {
        int chromCompare = compareChrom(this.getChrom(), chrom);
        if (chromCompare != 0) {
            return chromCompare;
        }

        int i1 = this.getStartPosition().compareTo(startpos);
        int i2 = this.getEndPosition().compareTo(endpos);
        if (i1 == 0 && i2 == 0) {
            return 0;
        }
        //sort by startpos, then endpos.
        if (i1 == 0) {
            return i2;
        } else {
            return i1;
        }

        //return this.getStartPosition().compareTo(startpos);// && this.getEndPosition().compareTo(endpos);
        //return this.getPosition().compareTo(pos);
    }

    public static int compareChrom(String chrom1, String chrom2) {
        chrom1 = chrom1.substring(3);
        chrom2 = chrom2.substring(3);
        try {
            if (NumberUtils.isNumber(chrom1) && NumberUtils.isNumber(chrom2)) {
                Integer a = Integer.parseInt(chrom1);
                Integer b = Integer.parseInt(chrom2);
                return a.compareTo(b);
            }

        } catch (NumberFormatException e) {
            //return chrom1.compareTo(chrom2);
        }
        return chrom1.compareTo(chrom2);
    }

    /*
     * CUSTOM INFO
     */
    public Integer getAlleleCount() {
        return alleleCount;
    }

    public Float getAlleleFrequency() {
        return alleleFrequency;
    }

    public String getAncestralAllele() {
        return ancestralAllele;
    }

    public Integer getBaseQuality() {
        return baseQuality;
    }

    public String getCigar() {
        return cigar;
    }

    public Boolean getDbSNPMembership() {
        return dbSNPMembership;
    }

    public Integer getDepthOfCoverage() {
        return depthOfCoverage;
    }

    public Boolean getHapmap2Membership() {
        return hapmap2Membership;
    }

    public Boolean getHapmap3Membership() {
        return hapmap3Membership;
    }

    public Boolean getIsInThousandGenomes() {
        return isInThousandGenomes;
    }

    public Boolean getIsSomatic() {
        return isSomatic;
    }

    public Boolean getIsValidated() {
        return isValidated;
    }

    public Integer getMappingQuality() {
        return mappingQuality;
    }

    public Integer getNumberOfAlleles() {
        return numberOfAlleles;
    }

    public Integer getNumberOfSamplesWithData() {
        return numberOfSamplesWithData;
    }

    public Integer getNumberOfZeroMQ() {
        return numberOfZeroMQ;
    }

    public Float getStrandBias() {
        return strandBias;
    }

    private Float extractFloatFromInfo(String key, String customInfo) {
        String val = extractValueFromInfo(key, customInfo);
        return (Float) VariantRecord.parse(Float.class, val);
    }

    private String extractStringFromInfo(String key, String customInfo) {
        String val = extractValueFromInfo(key, customInfo);
        return val;
    }

    private Integer extractIntegerFromInfo(String key, String customInfo) {
        String val = extractValueFromInfo(key, customInfo);
        return (Integer) VariantRecord.parse(Integer.class, val);
    }

    private Boolean extractBooleanFromInfo(String key, String customInfo) {
        Integer val = extractIntegerFromInfo(key, customInfo);
        if (val == null) {
            return null;
        }
        if (val == 1) {
            return true;
        } else {
            return false;
        }
    }

    private String extractValueFromInfo(String key, String customInfo) {

        String eKey = key + "=";
        int startIndex = customInfo.indexOf(eKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += eKey.length();
        String sub = customInfo.substring(startIndex);
        int endIndex = sub.indexOf(";");

        String result;
        if (endIndex == -1) {
            result = sub;
        } else {
            result = sub.substring(0, endIndex);
        }

        if (result.equals("")) {
            return null;
        } else {
            return result;
        }

    }

    private void extractInfo(String info) {
        this.ancestralAllele = extractStringFromInfo("AA", info);
        this.alleleCount = extractIntegerFromInfo("AC", info);
        this.alleleFrequency = extractFloatFromInfo("AF", info);
        this.numberOfAlleles = extractIntegerFromInfo("AN", info);
        this.baseQuality = extractIntegerFromInfo("BQ", info);
        this.cigar = extractStringFromInfo("CIGAR", info);
        this.dbSNPMembership = extractBooleanFromInfo("DB", info);
        this.depthOfCoverage = extractIntegerFromInfo("DP", info);
        //this.end_position = extractLongFromInfo("END", info); //We figure out END based on start, ref and alt.
        this.hapmap2Membership = extractBooleanFromInfo("H2", info);
        this.hapmap3Membership = extractBooleanFromInfo("H3", info);
        this.mappingQuality = extractIntegerFromInfo("MQ", info);
        this.numberOfZeroMQ = extractIntegerFromInfo("MQ0", info);
        this.numberOfSamplesWithData = extractIntegerFromInfo("NS", info);
        this.strandBias = extractFloatFromInfo("SB", info);
        this.isSomatic = extractBooleanFromInfo("SOMATIC", info);
        this.isValidated = extractBooleanFromInfo("VALIDATED", info);
        this.isInThousandGenomes = extractBooleanFromInfo("1000G", info);
    }

    /**
     * END CUSTOM INFO
     */
    @Override
    public String toString() {
        return "VariantRecord{" + "dnaID=" + dnaID + "chrom=" + chrom + "startpos=" + start_position + "endpos=" + end_position + "id=" + dbSNPID + "ref=" + ref + "alt=" + alt + "qual=" + qual + "filter=" + filter + '}';
    }
    private static String delim = "\t";

    public String toTabString(int uploadId, int fileId, int variantId) {
        String s
                = "\"" + getString(uploadId) + "\"" + delim
                + "\"" + getString(fileId) + "\"" + delim
                + "\"" + getString(variantId) + "\"" + delim
                + "\"" + getString(this.dnaID) + "\"" + delim
                + "\"" + getString(this.chrom) + "\"" + delim
                + "\"" + getString(this.start_position) + "\"" + delim
                + "\"" + getString(this.end_position) + "\"" + delim
                + "\"" + getString(this.dbSNPID) + "\"" + delim
                + "\"" + getString(this.ref) + "\"" + delim
                + "\"" + getString(this.alt) + "\"" + delim
                + "\"" + getString(this.altNumber) + "\"" + delim
                + "\"" + getString(this.qual) + "\"" + delim
                + "\"" + getString(this.filter) + "\"" + delim
                + "\"" + getString(this.type) + "\"" + delim
                + "\"" + getString(this.zygosity) + "\"" + delim
                + "\"" + getString(this.genotype) + "\"" + delim
                + "\"" + getString(this.customInfo) + "\"";// + delim;       
        return s;
    }

    public static String createTabString(Object[] values) {
        String s = "";
        if (values.length == 0) {
            return s;
        }
        for (Object o : values) {
            s += "\"" + StringEscapeUtils.escapeJava(getString(o)) + "\"" + delim;
        }
        return s.substring(0, s.length() - 1);
    }

    private static String getString(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof Boolean) {
            if ((Boolean) value) {
                return "1";
            } else {
                return "0";
            }
        } else {
            return value.toString();
        }
    }

    public void setSampleInformation(String format, String info) {
        String formatted = "FORMAT=" + format + ";SAMPLE_INFO=" + info;
        String newCustomInfo;
        if (customInfo == null) {
            newCustomInfo = formatted;
        } else {
            newCustomInfo = customInfo + ";" + formatted;
        }
        setCustomInfo(newCustomInfo);
    }
}
